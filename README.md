# HVAC Comfort Controller (Android Automotive OS)

An advanced, system-level Android Automotive OS (AAOS) application designed to control vehicle climate, seat comfort, defrosters, and vehicle state properties via the Vehicle Hardware Abstraction Layer (VHAL). Built with **Jetpack Compose**, **Material 3**, and **Kotlin Coroutines / StateFlow**.

---

## 🚀 Project Overview

The **HVAC Comfort Controller** is engineered to run as a privileged system application (`priv-app`) in automotive environments. It communicates directly with the underlying vehicle hardware services (`CarPropertyService` and `CarUxRestrictionService`) to provide a responsive, distraction-optimized touchscreen interface for vehicle occupants.

---

## 🛠️ Tech Stack & Architecture

- **Platform:** Android Automotive OS (AAOS) / Android SDK 37 (Min SDK 30)
- **UI Framework:** Jetpack Compose with Material 3 (Dark Theme optimized for automotive cockpits)
- **Concurrency & State:** Kotlin Coroutines & reactive `StateFlow`
- **Automotive APIs:** 
  - `android.car.Car`
  - `android.car.hardware.property.CarPropertyManager`
  - `android.car.drivingstate.CarUxRestrictionsManager`
- **Build System:** Gradle 9 with Configuration Cache optimization enabled

---

## 🚗 Core Features & Use Cases

### 1. Master Control & System Status
- **Master HVAC Power (`HVAC_POWER_ON`):** Instantly turns the entire climate ECU and blower system on or off to conserve vehicle power.
- **Gear State Monitoring (`CURRENT_GEAR`):** Detects whether the vehicle is in `PARK` or `DRIVING` state to enforce safety rules.
- **Temperature Units Toggle (`HVAC_TEMPERATURE_DISPLAY_UNITS`):** Instantly switches cabin temperature displays between Celsius (°C) and Fahrenheit (°F).
- **VHAL Synchronization:** Manual and automatic property resync to ensure UI state matches vehicle hardware.

### 2. Multi-Zone Climate Control
- **Dual-Zone Temperature (`HVAC_TEMPERATURE_SET`):** Independent micro-climate controls for Driver (`SEAT_ROW_1_LEFT`) and Passenger (`SEAT_ROW_1_RIGHT` / `ROW_2_RIGHT`). When dual-zone mode is disabled, driver adjustments automatically sync to the passenger zone.
- **Blower Fan Speed & RPM (`HVAC_FAN_SPEED` & `ACTUAL_FAN_SPEED_RPM`):** Adjusts blower speed (1–7) and reads actual mechanical blower RPM telemetry.
- **Airflow Vents (`HVAC_FAN_DIRECTION`):** Directs air via bitmask combinations (Face, Floor, Defrost).

### 3. Seat & Steering Comfort
- **Seat Heating & Cooling (`HVAC_SEAT_TEMPERATURE`):** Adjustable levels (-3 to +3) for rapid thermal comfort.
- **Seat Ventilation (`HVAC_SEAT_VENTILATION`):** Forced-air upholstery ventilation (0 to 3) for warm weather comfort.
- **Steering Wheel Heater (`HVAC_STEERING_WHEEL_HEAT`):** Adjustable rim heating (-3 to +3) for cold winter driving.

### 4. Climate Toggles & Defrosters
- **Primary Toggles:** A/C compressor, Auto climate, Air Recirculation, Front Windshield Defrost, and Dual-Zone.
- **Extended Toggles:** Max A/C, Max Defrost, Rear Defrost, Auto Recirculation, Side Mirror Heating (`HVAC_SIDE_MIRROR_HEAT`), and Electric Defroster (`HVAC_ELECTRIC_DEFROSTER_ON`).

---

## 🔌 VHAL Subscriptions & Real-Time Event Listening

The application utilizes `CarPropertyManager` to interact with VHAL:
- **Event Callback (`CarPropertyEventCallback`):** Listens asynchronously to `onChangeEvent` for all subscribed vehicle properties, instantly updating reactive `StateFlow` streams so the UI remains synchronized with physical hardware buttons or steering wheel controls.
- **Safe Initialization (`syncInitialState`):** Performs safe individual property reads (`runCatching`) so that if a specific vehicle trim lacks optional properties (like seat ventilation or actual fan RPM), initialization never crashes.
- **Lifecycle Resiliency:** `CarHvacManager` automatically binds to `CarService` (`Car.createCar`), registers sensor callbacks, and cleanly unregisters/disconnects on activity destruction.

---

## 🛑 Driving Safety & Distraction Optimization

- **UX Restrictions (`CarUxRestrictionsManager`):** Listens to driver distraction optimization requirements.
- **Gear-Based Safety Rule:** When shifted out of `PARK` into `DRIVE` or `REVERSE`, high blower speeds (>5) are automatically capped for safety and comfort, and distraction-heavy settings (such as profile photo/album edits) are locked.

---

## 📦 Privileged App Configuration (`priv-app`)

To access restricted car control APIs, the app requires privileged permissions declared in `AndroidManifest.xml`:
- `android.car.permission.CONTROL_CAR_CLIMATE`
- `android.car.permission.CAR_INFO`
- `android.car.permission.CAR_POWERTRAIN`
- Activity metadata: `distractionOptimized = true`
- Priv-app permission whitelist configuration in `res/assets/privapp-permissions-com.oem.hvac.xml`.
