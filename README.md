# TactoLM

Haptic communication system making smartphones accessible to deafblind users through AI-classified vibration patterns.

> Built for the GDG Bangalore Hackathon.

---

## Overview

For deafblind individuals, very few software solutions exist that allow them to interact with the digital world independently. Dedicated hardware alternatives such as refreshable Braille displays and robotic tactile devices cost upwards of ₹10,00,000 per unit, placing them out of reach for the vast majority of the 35,000+ deafblind individuals in Karnataka alone.

TactoLM runs on any Android phone they already own.

It translates the environment into a structured vocabulary of haptic patterns called Tactons. These patterns are engineered to target distinct mechanoreceptors in the fingertip. Each pattern is perceptually distinct. The user learns this vocabulary once, and from that point the phone communicates through touch.

---

## Features

**Vision Scan** —
The user photographs their environment. Gemini analyzes the image and identifies objects by safety priority, including hazards, people, furniture, food, and animals. Each category fires its corresponding Tacton in sequence, giving the user a tactile picture of their surroundings.

**Notification Intelligence** —
Incoming notifications are semantically classified in real time. Emergency alerts, health messages, and social notifications each produce a distinct haptic signal. The user understands the nature of the information without reading a single word.

**Doorbell Detection** —
A persistent background service listens for doorbell audio via TensorFlow Lite. On detection, a dedicated Tacton fires immediately, alerting the user that someone is at their door.

**Tacton Library** —
A custom haptic engine built on Android's VibrationEffect .createWaveform() API produces precise waveform patterns such as PULSE_BURST, HEALTH_RAMP, SLOW_RAMP, NAV_SLIDE.

**Teach Mode** —
A structured onboarding session that pairs each Tacton with physical hand signing by an interpreter, building the user's haptic vocabulary before independent use.

---

## Tech Stack

- **Google Gemini 2.5 Flash** — semantic classification of visual scenes via the native Android generativeai SDK
- **TensorFlow Lite Audio** — on-device audio classification
- **AndroidX CameraX** — single photo capture for the vision scan feature
- **Kotlin** — coroutines for async pipeline management, VibrationEffect API for haptic dispatch
- **Android SDK** — API 28 minimum

---

## Setup

### Prerequisites
- Android Studio Koala or later
- JDK 17
- Physical Android device running Android 9.0 (API 28) or higher
- A device with a high-quality LRA motor is recommended (tested on Poco X7 Pro)

### Installation
1. Clone the Repository

2. Obtain a Gemini API key from Google AI Studio (https://aistudio.google.com) and add it to your local.properties file in the project root:
```
GEMINI_API_KEY=your_api_key_here
```

Do not commit local.properties to version control.

3. Open the project in Android Studio, sync Gradle, connect your device, and run.

---

## Roadmap

The current system communicates category and urgency. The next stage is full semantic content  a vibrotactile encoding system where any arbitrary text can be received as haptic pulses on the same phone, functioning as a complete Braille display replacement at zero hardware cost.
