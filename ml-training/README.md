# Orbit ML training (travel-scene photo classifier)

Trains the on-device TFLite model used by the Android app
(`app/src/main/java/com/pnu/orbit/ml/TfLitePhotoClassifier.kt`).

- **Dataset:** Intel Image Classification (`seg_train`), 6 classes:
  `buildings, forest, glacier, mountain, sea, street` (~14k JPGs, 150×150).
- **Model:** MobileNetV2 transfer learning, 224×224 input, normalized to `[-1, 1]`.
- **Output:** `outputs/photo_classifier.tflite` + `outputs/photo_labels.txt`.

The dataset, `outputs/`, and the venv are git-ignored (large / generated).

## Why WSL2

The RTX 4060 (Ada) is **not** supported by TensorFlow's native-Windows GPU build
(that path is frozen at TF ≤ 2.10 / CUDA 11.2). WSL2 + TF-GPU supports Ada and
exports `.tflite` natively, so we train there. WSL2 Ubuntu-22.04 is already
installed on this machine.

## 1. Prepare the dataset

From Windows, unzip the dataset into `ml-training/data/` so you get
`ml-training/data/seg_train/<class>/*.jpg`.

```powershell
Expand-Archive -Path .\seg_train.zip -DestinationPath .\ml-training\data\
```

## 2. WSL2 + TF-GPU setup (one time)

> Do **not** install an NVIDIA driver or `cuda-toolkit` inside WSL. WSL2 uses the
> Windows driver (560.94) via passthrough; CUDA runtime libs come from the
> `tensorflow[and-cuda]` pip wheels.

```bash
wsl -d Ubuntu-22.04            # from a Windows terminal

# inside Ubuntu:
nvidia-smi                     # should show the RTX 4060 (driver passthrough)
sudo apt update && sudo apt install -y python3-venv python3-pip
python3 -m venv ~/orbit-ml
source ~/orbit-ml/bin/activate
pip install -U pip
pip install "tensorflow[and-cuda]==2.15.1"

# verify GPU is visible to TF:
python -c "import tensorflow as tf; print(tf.config.list_physical_devices('GPU'))"
# -> [PhysicalDevice(name='/physical_device:GPU:0', device_type='GPU')]
```

## 3. Train + export

```bash
source ~/orbit-ml/bin/activate
cd /mnt/c/Users/mseoky/AndroidStudioProjects/PNU_SDL/orbit-android/ml-training
python train.py
# options: --epochs 10 --fine-tune-epochs 5 --data-dir <path>
```

Produces `outputs/photo_classifier.tflite` and `outputs/photo_labels.txt`,
and prints the final validation accuracy and the index→label order.

## 4. Attach to the app

```bash
mkdir -p ../app/src/main/assets/ml
cp outputs/photo_classifier.tflite outputs/photo_labels.txt ../app/src/main/assets/ml/
```

The app loads these from `assets/ml/`. `photo_labels.txt` order must match the
`PhotoTag` enum names (case-insensitive). Then build/run from Android Studio.
