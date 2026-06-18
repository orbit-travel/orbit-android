"""
Train an on-device travel-scene classifier for the Orbit Android app.

Dataset: Intel Image Classification (seg_train), 6 classes
    buildings, forest, glacier, mountain, sea, street

Pipeline:
  - MobileNetV2 transfer learning, 224x224 input.
  - Inputs normalized to [-1, 1] via mobilenet_v2.preprocess_input IN THE DATA
    PIPELINE ONLY (no rescale/preprocess layer baked into the model), so the
    exported .tflite expects already-normalized [-1, 1] float input. This matches
    exactly what app/.../ml/TfLitePhotoClassifier.kt feeds at inference time
    (224x224, mean=127.5, std=127.5).

Outputs (written to ./outputs):
  - photo_classifier.tflite   (dynamic-range quantized; float in/out preserved)
  - photo_labels.txt          (class names in training order, one per line)
  - saved_model/              (Keras model, for reference / re-export)

Run inside WSL2 with TF-GPU (see README.md):
    python train.py
Optional overrides:
    python train.py --data-dir /path/to/seg_train --epochs 10 --fine-tune-epochs 5
"""

import argparse
import os

import tensorflow as tf
from tensorflow.keras.applications import MobileNetV2
from tensorflow.keras.applications.mobilenet_v2 import preprocess_input

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_DATA_DIR = os.path.join(HERE, "data", "seg_train")
OUTPUT_DIR = os.path.join(HERE, "outputs")

IMG_SIZE = (224, 224)
BATCH_SIZE = 32
SEED = 123


def parse_args():
    p = argparse.ArgumentParser()
    p.add_argument("--data-dir", default=DEFAULT_DATA_DIR,
                   help="Path to seg_train/<class>/*.jpg")
    p.add_argument("--epochs", type=int, default=10,
                   help="Frozen-base transfer-learning epochs")
    p.add_argument("--fine-tune-epochs", type=int, default=5,
                   help="Fine-tuning epochs (0 to skip)")
    p.add_argument("--val-split", type=float, default=0.2)
    return p.parse_args()


def report_gpu():
    gpus = tf.config.list_physical_devices("GPU")
    print(f"TensorFlow {tf.__version__}")
    print(f"GPUs visible: {gpus}")
    if not gpus:
        print("WARNING: no GPU detected — training will run on CPU.")


def build_datasets(data_dir, val_split):
    if not os.path.isdir(data_dir):
        raise SystemExit(
            f"Dataset not found at {data_dir}\n"
            "Unzip seg_train.zip into ml-training/data/ first (see README.md)."
        )

    train_ds = tf.keras.utils.image_dataset_from_directory(
        data_dir,
        validation_split=val_split,
        subset="training",
        seed=SEED,
        image_size=IMG_SIZE,
        batch_size=BATCH_SIZE,
        label_mode="categorical",
    )
    val_ds = tf.keras.utils.image_dataset_from_directory(
        data_dir,
        validation_split=val_split,
        subset="validation",
        seed=SEED,
        image_size=IMG_SIZE,
        batch_size=BATCH_SIZE,
        label_mode="categorical",
    )

    class_names = list(train_ds.class_names)  # alphabetical order
    print(f"Classes ({len(class_names)}): {class_names}")

    autotune = tf.data.AUTOTUNE
    # Light augmentation on the training set only.
    augment = tf.keras.Sequential([
        tf.keras.layers.RandomFlip("horizontal"),
        tf.keras.layers.RandomRotation(0.05),
    ])

    def prep_train(x, y):
        x = augment(x, training=True)
        return preprocess_input(x), y

    def prep_eval(x, y):
        return preprocess_input(x), y

    train_ds = train_ds.map(prep_train, num_parallel_calls=autotune).prefetch(autotune)
    val_ds = val_ds.map(prep_eval, num_parallel_calls=autotune).prefetch(autotune)
    return train_ds, val_ds, class_names


def build_model(num_classes):
    base = MobileNetV2(weights="imagenet", include_top=False,
                       input_shape=IMG_SIZE + (3,))
    base.trainable = False

    inputs = tf.keras.Input(shape=IMG_SIZE + (3,))  # expects [-1, 1] floats
    x = base(inputs, training=False)
    x = tf.keras.layers.GlobalAveragePooling2D()(x)
    x = tf.keras.layers.Dropout(0.2)(x)
    outputs = tf.keras.layers.Dense(num_classes, activation="softmax")(x)
    model = tf.keras.Model(inputs, outputs)
    model.compile(optimizer=tf.keras.optimizers.Adam(1e-3),
                  loss="categorical_crossentropy",
                  metrics=["accuracy"])
    return model, base


def fine_tune(model, base, train_ds, val_ds, epochs):
    if epochs <= 0:
        return
    # Unfreeze the top of the base for a low-LR fine-tune.
    base.trainable = True
    for layer in base.layers[:-30]:
        layer.trainable = False
    model.compile(optimizer=tf.keras.optimizers.Adam(1e-5),
                  loss="categorical_crossentropy",
                  metrics=["accuracy"])
    print(f"\n=== Fine-tuning for {epochs} epochs ===")
    model.fit(train_ds, validation_data=val_ds, epochs=epochs)


def export(model, class_names):
    os.makedirs(OUTPUT_DIR, exist_ok=True)

    saved_model_dir = os.path.join(OUTPUT_DIR, "saved_model")
    model.save(saved_model_dir)
    print(f"Saved Keras model -> {saved_model_dir}")

    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    # Dynamic-range quantization: weights -> int8, but float32 in/out preserved,
    # so the app's float [-1,1] input path keeps working. ~9MB -> ~3MB.
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite_model = converter.convert()

    tflite_path = os.path.join(OUTPUT_DIR, "photo_classifier.tflite")
    with open(tflite_path, "wb") as f:
        f.write(tflite_model)
    size_mb = os.path.getsize(tflite_path) / 1e6
    print(f"Wrote {tflite_path} ({size_mb:.2f} MB)")

    labels_path = os.path.join(OUTPUT_DIR, "photo_labels.txt")
    with open(labels_path, "w", encoding="utf-8") as f:
        f.write("\n".join(class_names) + "\n")
    print(f"Wrote {labels_path}")
    print("\nLabel order (index -> PhotoTag):")
    for i, name in enumerate(class_names):
        print(f"  {i}: {name} -> {name.upper()}")


def main():
    args = parse_args()
    report_gpu()
    train_ds, val_ds, class_names = build_datasets(args.data_dir, args.val_split)

    model, base = build_model(len(class_names))
    print(f"\n=== Transfer learning (frozen base) for {args.epochs} epochs ===")
    model.fit(train_ds, validation_data=val_ds, epochs=args.epochs)

    fine_tune(model, base, train_ds, val_ds, args.fine_tune_epochs)

    val_loss, val_acc = model.evaluate(val_ds)
    print(f"\nFinal validation accuracy: {val_acc:.4f} (loss {val_loss:.4f})")

    export(model, class_names)
    print("\nDone. Copy outputs/photo_classifier.tflite and outputs/photo_labels.txt "
          "into app/src/main/assets/ml/")


if __name__ == "__main__":
    main()
