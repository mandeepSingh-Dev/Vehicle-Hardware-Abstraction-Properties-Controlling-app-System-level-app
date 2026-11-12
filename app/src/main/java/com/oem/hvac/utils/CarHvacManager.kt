package com.oem.hvac.utils

import android.car.Car
import android.car.VehicleAreaSeat
import android.car.VehicleAreaWindow
import android.car.VehicleGear
import android.car.VehicleHvacFanDirection
import android.car.VehiclePropertyIds
import android.car.VehicleUnit
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages HVAC (Heating, Ventilation, and Air Conditioning) operations, VHAL (Vehicle Hardware Abstraction Layer)
 * property subscriptions, hardware capability bounds, and driving safety restrictions for Android Automotive OS (AAOS).
 *
 * This class interfaces directly with [CarPropertyManager] and [CarUxRestrictionsManager] to provide
 * reactive [MutableStateFlow] streams for UI consumption.
 *
 * @property context The application or activity context used to establish connection with the Car service.
 */
class CarHvacManager(private val context: Context) {

    private var car: Car? = null
    private var carPropertyManager: CarPropertyManager? = null
    private var uxRestrictionsManager: CarUxRestrictionsManager? = null

    // Hardware min/max bounds (loaded dynamically from VHAL configuration)
    var minDriverTemp: Float = 16.0f
        private set
    var maxDriverTemp: Float = 28.0f
        private set
    var minPassengerTemp: Float = 16.0f
        private set
    var maxPassengerTemp: Float = 28.0f
        private set
    var minFanSpeed: Int = 1
        private set
    var maxFanSpeed: Int = 7
        private set
    var minSeatTemp: Int = -3
        private set
    var maxSeatTemp: Int = 3
        private set
    var minSeatVentilation: Int = 0
        private set
    var maxSeatVentilation: Int = 3
        private set
    var minSteeringWheelHeat: Int = -3
        private set
    var maxSteeringWheelHeat: Int = 3
        private set

    // Active Area IDs detected dynamically from VHAL configuration
    private var passengerAreaId: Int = VehicleAreaSeat.SEAT_ROW_1_RIGHT
    private var fanAreaId: Int = VehicleAreaSeat.SEAT_ROW_1_LEFT
    private var globalHvacAreaId: Int = VehicleAreaSeat.SEAT_ROW_1_LEFT

    // --- Climate StateFlows (Internal temperature values are always stored in Celsius) ---
    private val _driverTemp = MutableStateFlow(22.0f)
    val driverTemp = _driverTemp.asStateFlow()

    private val _passengerTemp = MutableStateFlow(22.0f)
    val passengerTemp = _passengerTemp.asStateFlow()

    private val _fanSpeed = MutableStateFlow(1)
    val fanSpeed = _fanSpeed.asStateFlow()

    // Seat Heating/Cooling levels (-3 to +3)
    private val _driverSeatTemp = MutableStateFlow(0)
    val driverSeatTemp = _driverSeatTemp.asStateFlow()

    private val _passengerSeatTemp = MutableStateFlow(0)
    val passengerSeatTemp = _passengerSeatTemp.asStateFlow()

    // Airflow Direction Bitmask (FACE, FLOOR, DEFROST combinations)
    private val _fanDirection = MutableStateFlow(VehicleHvacFanDirection.FACE)
    val fanDirection = _fanDirection.asStateFlow()

    // Boolean Climate Toggles
    private val _isAcOn = MutableStateFlow(false)
    val isAcOn = _isAcOn.asStateFlow()

    private val _isAutoOn = MutableStateFlow(false)
    val isAutoOn = _isAutoOn.asStateFlow()

    private val _isRecircOn = MutableStateFlow(false)
    val isRecircOn = _isRecircOn.asStateFlow()

    private val _isFrontDefrostOn = MutableStateFlow(false)
    val isFrontDefrostOn = _isFrontDefrostOn.asStateFlow()

    private val _isDualZoneOn = MutableStateFlow(true)
    val isDualZoneOn = _isDualZoneOn.asStateFlow()

    // Display Unit Flag (true = Fahrenheit, false = Celsius)
    private val _isFahrenheit = MutableStateFlow(false)
    val isFahrenheit = _isFahrenheit.asStateFlow()

    // Driving Safety State (Distraction Optimization)
    private val _isDistractionOptimizedRequired = MutableStateFlow(false)
    val isDistractionOptimizedRequired = _isDistractionOptimizedRequired.asStateFlow()

    // --- Additional HVAC & Vehicle StateFlows ---
    private val _isHvacPowerOn = MutableStateFlow(true)
    val isHvacPowerOn = _isHvacPowerOn.asStateFlow()

    private val _isMaxAcOn = MutableStateFlow(false)
    val isMaxAcOn = _isMaxAcOn.asStateFlow()

    private val _isMaxDefrostOn = MutableStateFlow(false)
    val isMaxDefrostOn = _isMaxDefrostOn.asStateFlow()

    private val _isRearDefrostOn = MutableStateFlow(false)
    val isRearDefrostOn = _isRearDefrostOn.asStateFlow()

    private val _isAutoRecircOn = MutableStateFlow(false)
    val isAutoRecircOn = _isAutoRecircOn.asStateFlow()

    private val _isSideMirrorHeatOn = MutableStateFlow(false)
    val isSideMirrorHeatOn = _isSideMirrorHeatOn.asStateFlow()

    private val _isElectricDefrosterOn = MutableStateFlow(false)
    val isElectricDefrosterOn = _isElectricDefrosterOn.asStateFlow()

    private val _driverSeatVentilation = MutableStateFlow(0)
    val driverSeatVentilation = _driverSeatVentilation.asStateFlow()

    private val _passengerSeatVentilation = MutableStateFlow(0)
    val passengerSeatVentilation = _passengerSeatVentilation.asStateFlow()

    private val _steeringWheelHeat = MutableStateFlow(0)
    val steeringWheelHeat = _steeringWheelHeat.asStateFlow()

    private val _actualFanSpeedRpm = MutableStateFlow(0)
    val actualFanSpeedRpm = _actualFanSpeedRpm.asStateFlow()

    private val _currentGear = MutableStateFlow(VehicleGear.GEAR_PARK)
    val currentGear = _currentGear.asStateFlow()

    private val _isParked = MutableStateFlow(true)
    val isParked = _isParked.asStateFlow()

    /**
     * Real-time callback listener receiving change events from the Vehicle Hardware Abstraction Layer (VHAL).
     * Updates corresponding [StateFlow] properties whenever a subscribed vehicle property value changes.
     */
    private val propertyCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>?) {
            when (value?.propertyId) {
                VehiclePropertyIds.HVAC_TEMPERATURE_SET -> {
                    val temp = (value.value as? Float) ?: return
                    if (value.areaId and VehicleAreaSeat.SEAT_ROW_1_LEFT != 0) {
                        _driverTemp.value = temp
                    } else if (value.areaId and (VehicleAreaSeat.SEAT_ROW_1_RIGHT or VehicleAreaSeat.SEAT_ROW_2_RIGHT) != 0) {
                        _passengerTemp.value = temp
                    }
                }

                VehiclePropertyIds.HVAC_FAN_SPEED -> {
                    _fanSpeed.value = (value.value as? Int) ?: return
                }

                VehiclePropertyIds.HVAC_ACTUAL_FAN_SPEED_RPM -> {
                    _actualFanSpeedRpm.value = (value.value as? Int) ?: return
                }

                VehiclePropertyIds.HVAC_SEAT_TEMPERATURE -> {
                    val seatLevel = (value.value as? Int) ?: return
                    if (value.areaId and VehicleAreaSeat.SEAT_ROW_1_LEFT != 0) {
                        _driverSeatTemp.value = seatLevel
                    } else if (value.areaId and (VehicleAreaSeat.SEAT_ROW_1_RIGHT or VehicleAreaSeat.SEAT_ROW_2_RIGHT) != 0) {
                        _passengerSeatTemp.value = seatLevel
                    }
                }

                VehiclePropertyIds.HVAC_SEAT_VENTILATION -> {
                    val ventLevel = (value.value as? Int) ?: return
                    if (value.areaId and VehicleAreaSeat.SEAT_ROW_1_LEFT != 0) {
                        _driverSeatVentilation.value = ventLevel
                    } else if (value.areaId and (VehicleAreaSeat.SEAT_ROW_1_RIGHT or VehicleAreaSeat.SEAT_ROW_2_RIGHT) != 0) {
                        _passengerSeatVentilation.value = ventLevel
                    }
                }

                VehiclePropertyIds.HVAC_STEERING_WHEEL_HEAT -> {
                    _steeringWheelHeat.value = (value.value as? Int) ?: return
                }

                VehiclePropertyIds.HVAC_FAN_DIRECTION -> {
                    _fanDirection.value = (value.value as? Int) ?: return
                }

                VehiclePropertyIds.HVAC_POWER_ON -> {
                    _isHvacPowerOn.value = (value.value as? Boolean) ?: return
                }

                VehiclePropertyIds.HVAC_AC_ON -> {
                    _isAcOn.value = (value.value as? Boolean) ?: return
                }

                VehiclePropertyIds.HVAC_MAX_AC_ON -> {
                    _isMaxAcOn.value = (value.value as? Boolean) ?: return
                }

                VehiclePropertyIds.HVAC_AUTO_ON -> {
                    _isAutoOn.value = (value.value as? Boolean) ?: return
                }

                VehiclePropertyIds.HVAC_RECIRC_ON -> {
                    _isRecircOn.value = (value.value as? Boolean) ?: return
                }

                VehiclePropertyIds.HVAC_AUTO_RECIRC_ON -> {
                    _isAutoRecircOn.value = (value.value as? Boolean) ?: return
                }

                VehiclePropertyIds.HVAC_DUAL_ON -> {
                    _isDualZoneOn.value = (value.value as? Boolean) ?: return
                }

                VehiclePropertyIds.HVAC_MAX_DEFROST_ON -> {
                    _isMaxDefrostOn.value = (value.value as? Boolean) ?: return
                }

                VehiclePropertyIds.HVAC_DEFROSTER -> {
                    val active = (value.value as? Boolean) ?: return
                    if (value.areaId and VehicleAreaWindow.WINDOW_FRONT_WINDSHIELD != 0) {
                        _isFrontDefrostOn.value = active
                    }
                    if (value.areaId and VehicleAreaWindow.WINDOW_REAR_WINDSHIELD != 0) {
                        _isRearDefrostOn.value = active
                    }
                }

                VehiclePropertyIds.HVAC_SIDE_MIRROR_HEAT -> {
                    val heatVal = value.value
                    _isSideMirrorHeatOn.value = when (heatVal) {
                        is Boolean -> heatVal
                        is Int -> heatVal > 0
                        else -> return
                    }
                }

                VehiclePropertyIds.HVAC_ELECTRIC_DEFROSTER_ON -> {
                    _isElectricDefrosterOn.value = (value.value as? Boolean) ?: return
                }

                VehiclePropertyIds.HVAC_TEMPERATURE_DISPLAY_UNITS -> {
                    val unit = (value.value as? Int) ?: return
                    _isFahrenheit.value = (unit == VehicleUnit.FAHRENHEIT)
                }

                VehiclePropertyIds.CURRENT_GEAR -> {
                    val gear = (value.value as? Int) ?: return
                    _currentGear.value = gear
                    val parked = (gear == VehicleGear.GEAR_PARK)
                    _isParked.value = parked
                    onDrivingStateChanged(isParked = parked)
                }
            }
        }

        override fun onErrorEvent(propId: Int, zone: Int) {
            Log.e("CarHvacManager", "VHAL error on property $propId in zone $zone")
        }
    }

    /**
     * Establishes a lifecycle-resilient connection to the Android Car Service.
     * Automatically binds managers, loads hardware bounds, syncs initial state, and registers sensors upon connection.
     */
    fun connect() {
        car = Car.createCar(context, null, Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER) { carInstance, isReady ->
            if (isReady) {
                Log.d("CarHvacManager", "CarService connected")
                carPropertyManager = carInstance.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
                uxRestrictionsManager = carInstance.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager

                initHardwareConfig()
                syncInitialState()
                registerSensors()
                registerUxRestrictions()
            } else {
                Log.w("CarHvacManager", "CarService disconnected, clearing stale managers")
                carPropertyManager = null
                uxRestrictionsManager = null
            }
        }
    }

    /**
     * Queries VHAL configuration for supported properties and determines min/max operating limits and zone Area IDs.
     */
    private fun initHardwareConfig() {
        val manager = carPropertyManager ?: return

        manager.getCarPropertyConfig(VehiclePropertyIds.HVAC_TEMPERATURE_SET)?.let { tempConfig ->
            minDriverTemp = tempConfig.getMinValue(VehicleAreaSeat.SEAT_ROW_1_LEFT) as? Float ?: 16.0f
            maxDriverTemp = tempConfig.getMaxValue(VehicleAreaSeat.SEAT_ROW_1_LEFT) as? Float ?: 28.0f

            passengerAreaId = if (tempConfig.areaIds.contains(VehicleAreaSeat.SEAT_ROW_1_RIGHT)) {
                VehicleAreaSeat.SEAT_ROW_1_RIGHT
            } else {
                VehicleAreaSeat.SEAT_ROW_2_RIGHT
            }
            minPassengerTemp = tempConfig.getMinValue(passengerAreaId) as? Float ?: minDriverTemp
            maxPassengerTemp = tempConfig.getMaxValue(passengerAreaId) as? Float ?: maxDriverTemp
        }

        manager.getCarPropertyConfig(VehiclePropertyIds.HVAC_FAN_SPEED)?.let { fanConfig ->
            fanAreaId = fanConfig.areaIds.firstOrNull() ?: 0
            minFanSpeed = fanConfig.getMinValue(fanAreaId) as? Int ?: 1
            maxFanSpeed = fanConfig.getMaxValue(fanAreaId) as? Int ?: 7
        }

        manager.getCarPropertyConfig(VehiclePropertyIds.HVAC_SEAT_TEMPERATURE)?.let { seatConfig ->
            minSeatTemp = seatConfig.getMinValue(VehicleAreaSeat.SEAT_ROW_1_LEFT) as? Int ?: -3
            maxSeatTemp = seatConfig.getMaxValue(VehicleAreaSeat.SEAT_ROW_1_LEFT) as? Int ?: 3
        }

        manager.getCarPropertyConfig(VehiclePropertyIds.HVAC_SEAT_VENTILATION)?.let { ventConfig ->
            minSeatVentilation = ventConfig.getMinValue(VehicleAreaSeat.SEAT_ROW_1_LEFT) as? Int ?: 0
            maxSeatVentilation = ventConfig.getMaxValue(VehicleAreaSeat.SEAT_ROW_1_LEFT) as? Int ?: 3
        }

        manager.getCarPropertyConfig(VehiclePropertyIds.HVAC_STEERING_WHEEL_HEAT)?.let { steerConfig ->
            val area = steerConfig.areaIds.firstOrNull() ?: 0
            minSteeringWheelHeat = steerConfig.getMinValue(area) as? Int ?: -3
            maxSteeringWheelHeat = steerConfig.getMaxValue(area) as? Int ?: 3
        }

        manager.getCarPropertyConfig(VehiclePropertyIds.HVAC_AC_ON)?.let { acConfig ->
            globalHvacAreaId = acConfig.areaIds.firstOrNull() ?: VehicleAreaSeat.SEAT_ROW_1_LEFT
        }
    }

    /**
     * Synchronizes initial HVAC and vehicle states from VHAL using safe individual reads
     * so that a missing property on specific vehicle trims will not block other reads.
     */
    fun syncInitialState() {
        val manager = carPropertyManager ?: return

        runCatching { _driverTemp.value = manager.getFloatProperty(VehiclePropertyIds.HVAC_TEMPERATURE_SET, VehicleAreaSeat.SEAT_ROW_1_LEFT) }
        runCatching { _passengerTemp.value = manager.getFloatProperty(VehiclePropertyIds.HVAC_TEMPERATURE_SET, passengerAreaId) }
        runCatching { _fanSpeed.value = manager.getIntProperty(VehiclePropertyIds.HVAC_FAN_SPEED, fanAreaId) }
        runCatching { _actualFanSpeedRpm.value = manager.getIntProperty(VehiclePropertyIds.HVAC_ACTUAL_FAN_SPEED_RPM, fanAreaId) }
        runCatching { _driverSeatTemp.value = manager.getIntProperty(VehiclePropertyIds.HVAC_SEAT_TEMPERATURE, VehicleAreaSeat.SEAT_ROW_1_LEFT) }
        runCatching { _passengerSeatTemp.value = manager.getIntProperty(VehiclePropertyIds.HVAC_SEAT_TEMPERATURE, passengerAreaId) }
        runCatching { _driverSeatVentilation.value = manager.getIntProperty(VehiclePropertyIds.HVAC_SEAT_VENTILATION, VehicleAreaSeat.SEAT_ROW_1_LEFT) }
        runCatching { _passengerSeatVentilation.value = manager.getIntProperty(VehiclePropertyIds.HVAC_SEAT_VENTILATION, passengerAreaId) }
        runCatching { _steeringWheelHeat.value = manager.getIntProperty(VehiclePropertyIds.HVAC_STEERING_WHEEL_HEAT, 0) }
        runCatching { _fanDirection.value = manager.getIntProperty(VehiclePropertyIds.HVAC_FAN_DIRECTION, fanAreaId) }

        runCatching { _isHvacPowerOn.value = manager.getBooleanProperty(VehiclePropertyIds.HVAC_POWER_ON, globalHvacAreaId) }
        runCatching { _isAcOn.value = manager.getBooleanProperty(VehiclePropertyIds.HVAC_AC_ON, globalHvacAreaId) }
        runCatching { _isMaxAcOn.value = manager.getBooleanProperty(VehiclePropertyIds.HVAC_MAX_AC_ON, globalHvacAreaId) }
        runCatching { _isAutoOn.value = manager.getBooleanProperty(VehiclePropertyIds.HVAC_AUTO_ON, globalHvacAreaId) }
        runCatching { _isRecircOn.value = manager.getBooleanProperty(VehiclePropertyIds.HVAC_RECIRC_ON, globalHvacAreaId) }
        runCatching { _isAutoRecircOn.value = manager.getBooleanProperty(VehiclePropertyIds.HVAC_AUTO_RECIRC_ON, globalHvacAreaId) }
        runCatching { _isDualZoneOn.value = manager.getBooleanProperty(VehiclePropertyIds.HVAC_DUAL_ON, globalHvacAreaId) }
        runCatching { _isMaxDefrostOn.value = manager.getBooleanProperty(VehiclePropertyIds.HVAC_MAX_DEFROST_ON, globalHvacAreaId) }
        runCatching { _isFrontDefrostOn.value = manager.getBooleanProperty(VehiclePropertyIds.HVAC_DEFROSTER, VehicleAreaWindow.WINDOW_FRONT_WINDSHIELD) }
        runCatching { _isRearDefrostOn.value = manager.getBooleanProperty(VehiclePropertyIds.HVAC_DEFROSTER, VehicleAreaWindow.WINDOW_REAR_WINDSHIELD) }
        runCatching { _isElectricDefrosterOn.value = manager.getBooleanProperty(VehiclePropertyIds.HVAC_ELECTRIC_DEFROSTER_ON, VehicleAreaWindow.WINDOW_FRONT_WINDSHIELD) }

        runCatching {
            val unit = manager.getIntProperty(VehiclePropertyIds.HVAC_TEMPERATURE_DISPLAY_UNITS, 0)
            _isFahrenheit.value = (unit == VehicleUnit.FAHRENHEIT)
        }
        runCatching {
            val gear = manager.getIntProperty(VehiclePropertyIds.CURRENT_GEAR, 0)
            _currentGear.value = gear
            _isParked.value = (gear == VehicleGear.GEAR_PARK)
        }
    }

    /**
     * Subscribes to all relevant VHAL property change events for real-time updates.
     */
    private fun registerSensors() {
        val allProperties = listOf(
            VehiclePropertyIds.HVAC_POWER_ON,
            VehiclePropertyIds.HVAC_TEMPERATURE_SET,
            VehiclePropertyIds.HVAC_FAN_SPEED,
            VehiclePropertyIds.HVAC_ACTUAL_FAN_SPEED_RPM,
            VehiclePropertyIds.HVAC_SEAT_TEMPERATURE,
            VehiclePropertyIds.HVAC_SEAT_VENTILATION,
            VehiclePropertyIds.HVAC_STEERING_WHEEL_HEAT,
            VehiclePropertyIds.HVAC_FAN_DIRECTION,
            VehiclePropertyIds.HVAC_AC_ON,
            VehiclePropertyIds.HVAC_MAX_AC_ON,
            VehiclePropertyIds.HVAC_AUTO_ON,
            VehiclePropertyIds.HVAC_RECIRC_ON,
            VehiclePropertyIds.HVAC_AUTO_RECIRC_ON,
            VehiclePropertyIds.HVAC_DUAL_ON,
            VehiclePropertyIds.HVAC_MAX_DEFROST_ON,
            VehiclePropertyIds.HVAC_DEFROSTER,
            VehiclePropertyIds.HVAC_SIDE_MIRROR_HEAT,
            VehiclePropertyIds.HVAC_ELECTRIC_DEFROSTER_ON,
            VehiclePropertyIds.HVAC_TEMPERATURE_DISPLAY_UNITS,
            VehiclePropertyIds.CURRENT_GEAR
        )
        subscribeProperties(allProperties)
    }

    /**
     * Subscribes to a single VHAL property ID.
     */
    fun subscribeProperty(propId: Int, rate: Float = CarPropertyManager.SENSOR_RATE_ONCHANGE): Boolean {
        val manager = carPropertyManager ?: return false
        return runCatching {
            manager.registerCallback(propertyCallback, propId, rate)
        }.onFailure {
            Log.w("CarHvacManager", "Property $propId not supported on this vehicle: ${it.message}")
        }.getOrDefault(false)
    }

    /**
     * Subscribes to a list of VHAL property IDs.
     */
    fun subscribeProperties(propIds: List<Int>, rate: Float = CarPropertyManager.SENSOR_RATE_ONCHANGE) {
        propIds.forEach { propId -> subscribeProperty(propId, rate) }
    }

    /**
     * Unsubscribes from a VHAL property.
     */
    fun unsubscribeProperty(propId: Int) {
        carPropertyManager?.unregisterCallback(propertyCallback, propId)
    }

    // =========================================================================
    // GENERIC GET / SET / CAPABILITY HELPERS
    // =========================================================================

    fun isPropertySupported(propId: Int): Boolean =
        carPropertyManager?.getCarPropertyConfig(propId) != null

    fun getFloatProperty(propId: Int, areaId: Int): Float? =
        runCatching { carPropertyManager?.getFloatProperty(propId, areaId) }.getOrNull()

    fun getIntProperty(propId: Int, areaId: Int): Int? =
        runCatching { carPropertyManager?.getIntProperty(propId, areaId) }.getOrNull()

    fun getBooleanProperty(propId: Int, areaId: Int): Boolean? =
        runCatching { carPropertyManager?.getBooleanProperty(propId, areaId) }.getOrNull()

    fun setFloatProperty(propId: Int, areaId: Int, value: Float) {
        runCatching { carPropertyManager?.setFloatProperty(propId, areaId, value) }
    }

    fun setIntProperty(propId: Int, areaId: Int, value: Int) {
        runCatching { carPropertyManager?.setIntProperty(propId, areaId, value) }
    }

    fun setBooleanProperty(propId: Int, areaId: Int, value: Boolean) {
        runCatching { carPropertyManager?.setBooleanProperty(propId, areaId, value) }
    }

    // =========================================================================
    // CLAMPED VHAL WRITE METHODS
    // =========================================================================

    /** Sets driver zone target temperature in Celsius, respecting safety bounds and dual-zone state. */
    fun setDriverTemperature(tempCelsius: Float) {
        val safeTemp = tempCelsius.coerceIn(minDriverTemp, maxDriverTemp)
        carPropertyManager?.setFloatProperty(VehiclePropertyIds.HVAC_TEMPERATURE_SET, VehicleAreaSeat.SEAT_ROW_1_LEFT, safeTemp)
        if (!_isDualZoneOn.value) {
            setPassengerTemperature(safeTemp)
        }
    }

    /** Sets passenger zone target temperature in Celsius, respecting safety bounds. */
    fun setPassengerTemperature(tempCelsius: Float) {
        val safeTemp = tempCelsius.coerceIn(minPassengerTemp, maxPassengerTemp)
        carPropertyManager?.setFloatProperty(VehiclePropertyIds.HVAC_TEMPERATURE_SET, passengerAreaId, safeTemp)
    }

    /** Sets blower fan speed level, clamped to vehicle limits. */
    fun setFanSpeed(speed: Int) {
        val safeSpeed = speed.coerceIn(minFanSpeed, maxFanSpeed)
        carPropertyManager?.setIntProperty(VehiclePropertyIds.HVAC_FAN_SPEED, fanAreaId, safeSpeed)
    }

    /** Sets driver seat heating/cooling level (-3 to +3). */
    fun setDriverSeatTemp(level: Int) {
        val safeLevel = level.coerceIn(minSeatTemp, maxSeatTemp)
        carPropertyManager?.setIntProperty(VehiclePropertyIds.HVAC_SEAT_TEMPERATURE, VehicleAreaSeat.SEAT_ROW_1_LEFT, safeLevel)
    }

    /** Sets passenger seat heating/cooling level (-3 to +3). */
    fun setPassengerSeatTemp(level: Int) {
        val safeLevel = level.coerceIn(minSeatTemp, maxSeatTemp)
        carPropertyManager?.setIntProperty(VehiclePropertyIds.HVAC_SEAT_TEMPERATURE, passengerAreaId, safeLevel)
    }

    /** Sets airflow direction bitmask (Face, Floor, Defrost). */
    fun setFanDirection(directionMask: Int) {
        carPropertyManager?.setIntProperty(VehiclePropertyIds.HVAC_FAN_DIRECTION, fanAreaId, directionMask)
    }

    /** Toggles compressor A/C state. */
    fun setAcOn(enabled: Boolean) {
        carPropertyManager?.setBooleanProperty(VehiclePropertyIds.HVAC_AC_ON, globalHvacAreaId, enabled)
    }

    /** Toggles automatic climate control state. */
    fun setAutoOn(enabled: Boolean) {
        carPropertyManager?.setBooleanProperty(VehiclePropertyIds.HVAC_AUTO_ON, globalHvacAreaId, enabled)
    }

    /** Toggles air recirculation mode. */
    fun setRecircOn(enabled: Boolean) {
        carPropertyManager?.setBooleanProperty(VehiclePropertyIds.HVAC_RECIRC_ON, globalHvacAreaId, enabled)
    }

    /** Toggles dual-zone temperature synchronization. */
    fun setDualZoneOn(enabled: Boolean) {
        carPropertyManager?.setBooleanProperty(VehiclePropertyIds.HVAC_DUAL_ON, globalHvacAreaId, enabled)
    }

    /** Toggles front windshield defroster. */
    fun setFrontDefrostOn(enabled: Boolean) {
        carPropertyManager?.setBooleanProperty(VehiclePropertyIds.HVAC_DEFROSTER, VehicleAreaWindow.WINDOW_FRONT_WINDSHIELD, enabled)
    }

    /** Toggles overall system HVAC power. */
    fun setHvacPowerOn(enabled: Boolean) {
        carPropertyManager?.setBooleanProperty(VehiclePropertyIds.HVAC_POWER_ON, globalHvacAreaId, enabled)
    }

    /** Toggles Max A/C mode. */
    fun setMaxAcOn(enabled: Boolean) {
        carPropertyManager?.setBooleanProperty(VehiclePropertyIds.HVAC_MAX_AC_ON, globalHvacAreaId, enabled)
    }

    /** Toggles Max Defrost mode. */
    fun setMaxDefrostOn(enabled: Boolean) {
        carPropertyManager?.setBooleanProperty(VehiclePropertyIds.HVAC_MAX_DEFROST_ON, globalHvacAreaId, enabled)
    }

    /** Toggles rear windshield defroster. */
    fun setRearDefrostOn(enabled: Boolean) {
        carPropertyManager?.setBooleanProperty(VehiclePropertyIds.HVAC_DEFROSTER, VehicleAreaWindow.WINDOW_REAR_WINDSHIELD, enabled)
    }

    /** Toggles automatic air recirculation. */
    fun setAutoRecircOn(enabled: Boolean) {
        carPropertyManager?.setBooleanProperty(VehiclePropertyIds.HVAC_AUTO_RECIRC_ON, globalHvacAreaId, enabled)
    }

    /** Toggles electric defroster. */
    fun setElectricDefrosterOn(enabled: Boolean) {
        carPropertyManager?.setBooleanProperty(VehiclePropertyIds.HVAC_ELECTRIC_DEFROSTER_ON, VehicleAreaWindow.WINDOW_FRONT_WINDSHIELD, enabled)
    }

    /** Toggles side mirror heating. */
    fun setSideMirrorHeatOn(enabled: Boolean) {
        val areaId = carPropertyManager?.getCarPropertyConfig(VehiclePropertyIds.HVAC_SIDE_MIRROR_HEAT)
            ?.areaIds?.firstOrNull() ?: 0
        runCatching {
            carPropertyManager?.setIntProperty(VehiclePropertyIds.HVAC_SIDE_MIRROR_HEAT, areaId, if (enabled) 1 else 0)
        }.onFailure {
            carPropertyManager?.setBooleanProperty(VehiclePropertyIds.HVAC_SIDE_MIRROR_HEAT, areaId, enabled)
        }
    }

    /** Sets driver seat ventilation level. */
    fun setDriverSeatVentilation(level: Int) {
        val safeLevel = level.coerceIn(minSeatVentilation, maxSeatVentilation)
        carPropertyManager?.setIntProperty(VehiclePropertyIds.HVAC_SEAT_VENTILATION, VehicleAreaSeat.SEAT_ROW_1_LEFT, safeLevel)
    }

    /** Sets passenger seat ventilation level. */
    fun setPassengerSeatVentilation(level: Int) {
        val safeLevel = level.coerceIn(minSeatVentilation, maxSeatVentilation)
        carPropertyManager?.setIntProperty(VehiclePropertyIds.HVAC_SEAT_VENTILATION, passengerAreaId, safeLevel)
    }

    /** Sets steering wheel heating level. */
    fun setSteeringWheelHeat(level: Int) {
        val safeLevel = level.coerceIn(minSteeringWheelHeat, maxSteeringWheelHeat)
        val areaId = carPropertyManager?.getCarPropertyConfig(VehiclePropertyIds.HVAC_STEERING_WHEEL_HEAT)
            ?.areaIds?.firstOrNull() ?: 0
        carPropertyManager?.setIntProperty(VehiclePropertyIds.HVAC_STEERING_WHEEL_HEAT, areaId, safeLevel)
    }

    /** Switches climate display temperature units between Celsius and Fahrenheit. */
    fun setTemperatureDisplayUnits(useFahrenheit: Boolean) {
        val unit = if (useFahrenheit) VehicleUnit.FAHRENHEIT else VehicleUnit.CELSIUS
        carPropertyManager?.setIntProperty(VehiclePropertyIds.HVAC_TEMPERATURE_DISPLAY_UNITS, 0, unit)
    }

    // =========================================================================
    // DRIVING VS. PARKING STATE HANDLING
    // =========================================================================

    /**
     * Registers UX restriction listener to manage distraction optimization requirements while driving.
     */
    private fun registerUxRestrictions() {
        val uxManager = uxRestrictionsManager ?: return
        _isDistractionOptimizedRequired.value = uxManager.currentCarUxRestrictions?.isRequiresDistractionOptimization ?: false

        uxManager.registerListener { restrictions ->
            _isDistractionOptimizedRequired.value = restrictions.isRequiresDistractionOptimization
        }
    }

    /**
     * Enforces safety rules when driving state changes (e.g. limiting high fan noise when shifted out of Park).
     */
    private fun onDrivingStateChanged(isParked: Boolean) {
        if (!isParked) {
            // Vehicle shifted to Drive/Reverse: cap high blower fan noise for safety and comfort
            if (_fanSpeed.value > 5) {
                setFanSpeed(5)
            }
            Log.d("CarHvacManager", "Vehicle driving: distraction rules enforced")
        } else {
            Log.d("CarHvacManager", "Vehicle parked: full HVAC & profile controls unlocked")
        }
    }

    /**
     * Formats a temperature in Celsius into either Celsius or Fahrenheit string based on active vehicle display units.
     */
    fun formatTemperature(tempCelsius: Float): String {
        return if (_isFahrenheit.value) {
            val fahrenheit = (tempCelsius * 9f / 5f) + 32f
            String.format("%.0f°F", fahrenheit)
        } else {
            String.format("%.1f°C", tempCelsius)
        }
    }

    /**
     * Cleans up VHAL callbacks and disconnects from Car service.
     */
    fun disconnect() {
        carPropertyManager?.unregisterCallback(propertyCallback)
        uxRestrictionsManager?.unregisterListener()
        car?.disconnect()
    }
}
