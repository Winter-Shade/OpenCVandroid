# 🧪 Real-Time Edge Detection Viewer (Android + OpenCV + OpenGL + Web)

A modular Android + C++ + TypeScript project that demonstrates **real-time camera frame processing** using **OpenCV (C++)**, **OpenGL ES rendering**, and a **TypeScript-based web viewer** for visualization and debugging.

---

## 📂 Project Structure

root/   
│
├── app/   
│ ├── src/   
│ │ └── main/   
│ │ ├── cpp/ #Native C++ processing   
│ │ │   └── CMakeLists.txt
│ │ │   └── opencvandroid.cpp
│ │ ├── java/   
│ │ │ ├── MainActivity.java # Main Android entry point   
│ │ │ ├── MyGLSurface.java # OpenGL Surface setup   
│ │ │ └── MyGLRenderer.java # Custom OpenGL Renderer   
│ │ └── res/ (layouts, drawables)     
│    
├── web/     
│ ├── src/    
│ │ └── index.ts # Loads static frames and overlays FPS, resolution    
│ ├── dist/   
│ ├── index.html    
│ ├── tsconfig.json   
│ ├── package.json    
│ └── README.md (optional)     
│    
└── README.md (this file)    
    
---    
    
## ⚙️ Setup Instructions    

## 🔗 Integration Setup: (OpenCV + Android)

Follow these steps to integrate **OpenCV** with your Android project:

### 🧩 Step 1: Download OpenCV SDK
- Go to the [OpenCV official website](https://opencv.org/releases/).
- Download the **OpenCV Android SDK** (e.g., `OpenCV-4.x-android-sdk.zip`).
- Extract it to a known location, for example:


---

### 🧱 Step 2: Import OpenCV Module
1. In **Android Studio**, go to:  
 **File → New → Import Module**
2. Select the SDK path:  
3. Name it as `:OpenCV` when prompted.
4. Wait for Gradle sync to complete.

---

### ⚙️ Step 3: Link OpenCV in Your App
Open your `app/build.gradle` file and add:

```gradle
implementation project(':OpenCV')   
 ```


🌐 Web Viewer Setup
Step 1: Go to the web directory
```
cd web
```

Step 2: Install dependencies
```
npm install
```

Step 3: Compile TypeScript
```
npx tsc
```

Step 4: Start a local server

Use any local dev server (e.g., Live Server, npx serve, or Python):

```
npx serve dist
```

Then open http://localhost:3000

Step 5: Add frames

Place your processed image frames in:
```
web/frames/
```

They’ll automatically be displayed by the viewer with overlay stats:
```
FPS
Resolution
```



