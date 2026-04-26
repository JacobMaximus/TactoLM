# TactoLM 🟢

> **A dual-track sensory substitution system providing high-fidelity haptic feedback for the deafblind.**
> Crafted with **Antigravity** for the GDG Bangalore Hackathon.

TactoLM bridges the gap between complex visual/auditory environments and the tactile senses. By combining ultra-low-latency on-device machine learning with the deep semantic reasoning of Google's Gemini, TactoLM translates the world into rich, structured haptic feedback (Tactons) that users can intuitively feel and understand.

## 🌟 Project Overview

For deafblind individuals, navigating the world requires an immense amount of cognitive load. TactoLM acts as a digital white cane with "eyes" and "ears". Instead of relying solely on slow braille displays or simple vibration motors, TactoLM introduces a **Dual-Track Architecture**:

- **Fast Track (On-Device):** Uses TensorFlow Lite for immediate, critical environmental audio classification (e.g., sirens, doorbells, sudden loud noises). This triggers highly tuned haptic patterns instantly.
- **Smart Track (Cloud):** Uses **Google Gemini 2.0 Flash** to process complex visual scenes and provide semantic distillation. Instead of reading out a visual description, Gemini analyzes the scene and assigns cognitive priority, feeding data back into our custom haptic `LRADispatcher`.

The entire experience is wrapped in a premium, glassmorphic dark-mode UI designed for high contrast and accessibility, driven by precise LRA (Linear Resonant Actuator) haptic waveforms.

---

## ⚡ Tech Stack (Powered by Google)

TactoLM heavily leverages state-of-the-art Google technologies to achieve both speed and intelligence:

- **[Google Gemini 2.0 Flash API]**: The brain of the "Smart Track". We utilize the native `generativeai` Android SDK to perform multi-modal reasoning on environmental data, allowing TactoLM to understand context (e.g., "A dog is running towards you") rather than just raw pixels.
- **[TensorFlow Lite Audio Tasks]**: Powers the "Fast Track". We run lightweight `tflite` models directly on-device to classify ambient audio (like doorbells or transit sounds) with zero network latency, ensuring user safety.
- **[AndroidX CameraX]**: Used for robust, lifecycle-aware camera session management to capture visual context for Gemini.
- **Kotlin & Android SDK**: The core application is built natively in Kotlin, utilizing Coroutines for asynchronous pipeline management and Material Design for the UI foundations.

---

## 🚀 Setup Instructions

Follow these steps to get TactoLM running locally on your Android device (A device with a high-quality LRA haptic motor, like the Poco X7 Pro, is highly recommended for the best experience).

### Prerequisites
- **Android Studio** (Koala or later recommended)
- **JDK 17**
- A physical Android device running Android 9.0 (API level 28) or higher.

### 1. Clone the Repository
```bash
git clone https://github.com/Arshath-AD/TactoLM.git
cd TactoLM
```

### 2. Configure Google Gemini API Key
TactoLM requires a Gemini API key to function. 
1. Get an API key from [Google AI Studio](https://aistudio.google.com/).
2. In your project root, open the `local.properties` file (or create it if it doesn't exist).
3. Add your API key:
   ```properties
   GEMINI_API_KEY=your_actual_api_key_here
   ```
*(Note: Do not commit your `local.properties` file to version control).*

### 3. Build and Run
1. Open the project in **Android Studio**.
2. Sync the project with Gradle files.
3. Connect your Android device via USB or Wireless Debugging.
4. Click **Run** (`Shift + F10`) to compile and deploy the `app-debug.apk` to your device.

---

## 🛠️ Key Features

- **Vision / Scene Summary:** Takes snapshots of the environment, passes them to Gemini 2.0, and maps the context to predefined Haptic Scenarios (Social, Transit, Emergency).
- **Doorbell Monitor:** A persistent background listener that identifies doorbell rings via TFLite and sends a distinct `pulse_burst` haptic pattern.
- **Teach Mode:** Allows the system to learn specific environmental triggers tailored to the user.
- **Tacton Haptic Library:** A custom Kotlin haptics engine (`TactonLibrary` & `LRADispatcher`) that generates precise waveform effects (`PULSE_BURST`, `HEALTH_RAMP`, `SLOW_RAMP`, `NAV_SLIDE`) rather than generic vibrations.

<br>

---
*Crafted with Antigravity*
