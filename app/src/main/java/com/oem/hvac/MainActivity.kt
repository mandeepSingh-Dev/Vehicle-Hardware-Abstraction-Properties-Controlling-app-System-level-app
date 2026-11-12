package com.oem.hvac

import android.car.VehicleHvacFanDirection
import android.car.VehiclePropertyIds
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oem.hvac.utils.CarHvacManager

/**
 * Main Activity for the HVAC Comfort Controller application on Android Automotive OS (AAOS).
 *
 * This activity initializes the [CarHvacManager] to bind to the vehicle's hardware property service,
 * registers distraction optimization and UX restriction listeners, and hosts the Jetpack Compose
 * UI for climate control.
 */
class MainActivity : ComponentActivity() {

    private lateinit var hvacManager: CarHvacManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize HVAC manager and connect to AAOS Car Service
        hvacManager = CarHvacManager(this)
        hvacManager.connect()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                HvacControlScreen(hvacManager)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ensure VHAL callbacks and Car service connection are safely torn down
        hvacManager.disconnect()
    }
}

/**
 * Main Jetpack Compose screen for the HVAC Comfort Controller.
 * Displays driver and passenger climate zones, fan speed and airflow direction, primary and extended
 * HVAC toggles, steering wheel heat, and driving safety restrictions.
 *
 * @param hvacManager Instance of [CarHvacManager] managing VHAL state and commands.
 */
@Composable
fun HvacControlScreen(hvacManager: CarHvacManager) {
    // --- Existing State Collectors ---
    val driverTemp by hvacManager.driverTemp.collectAsState()
    val passengerTemp by hvacManager.passengerTemp.collectAsState()
    val fanSpeed by hvacManager.fanSpeed.collectAsState()
    val driverSeatTemp by hvacManager.driverSeatTemp.collectAsState()
    val passengerSeatTemp by hvacManager.passengerSeatTemp.collectAsState()
    val fanDirection by hvacManager.fanDirection.collectAsState()

    val isAcOn by hvacManager.isAcOn.collectAsState()
    val isAutoOn by hvacManager.isAutoOn.collectAsState()
    val isRecircOn by hvacManager.isRecircOn.collectAsState()
    val isFrontDefrostOn by hvacManager.isFrontDefrostOn.collectAsState()
    val isDualZoneOn by hvacManager.isDualZoneOn.collectAsState()
    val isFahrenheit by hvacManager.isFahrenheit.collectAsState()
    val isDriving by hvacManager.isDistractionOptimizedRequired.collectAsState()

    // --- New State Collectors for Added Properties ---
    val isHvacPowerOn by hvacManager.isHvacPowerOn.collectAsState()
    val isMaxAcOn by hvacManager.isMaxAcOn.collectAsState()
    val isMaxDefrostOn by hvacManager.isMaxDefrostOn.collectAsState()
    val isRearDefrostOn by hvacManager.isRearDefrostOn.collectAsState()
    val isAutoRecircOn by hvacManager.isAutoRecircOn.collectAsState()
    val isSideMirrorHeatOn by hvacManager.isSideMirrorHeatOn.collectAsState()
    val isElectricDefrosterOn by hvacManager.isElectricDefrosterOn.collectAsState()
    val driverSeatVentilation by hvacManager.driverSeatVentilation.collectAsState()
    val passengerSeatVentilation by hvacManager.passengerSeatVentilation.collectAsState()
    val steeringWheelHeat by hvacManager.steeringWheelHeat.collectAsState()
    val actualFanSpeedRpm by hvacManager.actualFanSpeedRpm.collectAsState()
    val isParked by hvacManager.isParked.collectAsState()

    // Trigger memoized recomposition when temperature unit or raw temperature changes
    val driverTempLabel = remember(driverTemp, isFahrenheit) { hvacManager.formatTemperature(driverTemp) }
    val passengerTempLabel = remember(passengerTemp, isFahrenheit) { hvacManager.formatTemperature(passengerTemp) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // =====================================================================
        // TOP BAR: Master HVAC Power, Gear Status, Unit Toggle & VHAL Sync
        // =====================================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Master HVAC Power Button (Red when OFF, Primary when ON)
                Button(
                    onClick = { hvacManager.setHvacPowerOn(!isHvacPowerOn) },
                    modifier = Modifier.height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isHvacPowerOn) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(
                        text = if (isHvacPowerOn) "HVAC: ON" else "HVAC: OFF",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Vehicle Gear State Status Indicator (PARK vs DRIVE)
                Text(
                    text = if (isParked) "Gear: PARKED" else "Gear: DRIVING",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isParked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Temperature Units Toggle (°C / °F)
                OutlinedButton(
                    onClick = { hvacManager.setTemperatureDisplayUnits(!isFahrenheit) },
                    modifier = Modifier.height(56.dp)
                ) {
                    Text(text = if (isFahrenheit) "Unit: °F" else "Unit: °C", fontSize = 16.sp)
                }

                // Manual VHAL property state resync button
                OutlinedButton(
                    onClick = { hvacManager.syncInitialState() },
                    modifier = Modifier.height(56.dp)
                ) {
                    Text(text = "Sync VHAL", fontSize = 16.sp)
                }
            }
        }

        // =====================================================================
        // ROW 1: Driver Zone, Blower & Vent Direction, Passenger Zone
        // =====================================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ZoneControlCard(
                title = "Driver",
                formattedTemp = driverTempLabel,
                rawTemp = driverTemp,
                minTemp = hvacManager.minDriverTemp,
                maxTemp = hvacManager.maxDriverTemp,
                seatLevel = driverSeatTemp,
                minSeat = hvacManager.minSeatTemp,
                maxSeat = hvacManager.maxSeatTemp,
                seatVentLevel = driverSeatVentilation,
                minSeatVent = hvacManager.minSeatVentilation,
                maxSeatVent = hvacManager.maxSeatVentilation,
                showSeatVent = hvacManager.isPropertySupported(VehiclePropertyIds.HVAC_SEAT_VENTILATION),
                onTempDecrease = { hvacManager.setDriverTemperature(driverTemp - 0.5f) },
                onTempIncrease = { hvacManager.setDriverTemperature(driverTemp + 0.5f) },
                onSeatDecrease = { hvacManager.setDriverSeatTemp(driverSeatTemp - 1) },
                onSeatIncrease = { hvacManager.setDriverSeatTemp(driverSeatTemp + 1) },
                onSeatVentDecrease = { hvacManager.setDriverSeatVentilation(driverSeatVentilation - 1) },
                onSeatVentIncrease = { hvacManager.setDriverSeatVentilation(driverSeatVentilation + 1) }
            )

            FanAndAirflowCard(
                fanSpeed = fanSpeed,
                minSpeed = hvacManager.minFanSpeed,
                maxSpeed = hvacManager.maxFanSpeed,
                actualRpm = actualFanSpeedRpm,
                showRpm = hvacManager.isPropertySupported(VehiclePropertyIds.HVAC_ACTUAL_FAN_SPEED_RPM),
                fanDirection = fanDirection,
                onDecrease = { hvacManager.setFanSpeed(fanSpeed - 1) },
                onIncrease = { hvacManager.setFanSpeed(fanSpeed + 1) },
                onDirectionSelect = { hvacManager.setFanDirection(it) }
            )

            ZoneControlCard(
                title = "Passenger",
                formattedTemp = passengerTempLabel,
                rawTemp = passengerTemp,
                minTemp = hvacManager.minPassengerTemp,
                maxTemp = hvacManager.maxPassengerTemp,
                seatLevel = passengerSeatTemp,
                minSeat = hvacManager.minSeatTemp,
                maxSeat = hvacManager.maxSeatTemp,
                seatVentLevel = passengerSeatVentilation,
                minSeatVent = hvacManager.minSeatVentilation,
                maxSeatVent = hvacManager.maxSeatVentilation,
                showSeatVent = hvacManager.isPropertySupported(VehiclePropertyIds.HVAC_SEAT_VENTILATION),
                onTempDecrease = { hvacManager.setPassengerTemperature(passengerTemp - 0.5f) },
                onTempIncrease = { hvacManager.setPassengerTemperature(passengerTemp + 0.5f) },
                onSeatDecrease = { hvacManager.setPassengerSeatTemp(passengerSeatTemp - 1) },
                onSeatIncrease = { hvacManager.setPassengerSeatTemp(passengerSeatTemp + 1) },
                onSeatVentDecrease = { hvacManager.setPassengerSeatVentilation(passengerSeatVentilation - 1) },
                onSeatVentIncrease = { hvacManager.setPassengerSeatVentilation(passengerSeatVentilation + 1) }
            )
        }

        // =====================================================================
        // ROW 2: Primary Climate Toggles (A/C, AUTO, RECIRC, DEFROST, DUAL)
        // =====================================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ClimateToggleButton("A/C", isAcOn, Modifier.weight(1f)) { hvacManager.setAcOn(!isAcOn) }
            ClimateToggleButton("AUTO", isAutoOn, Modifier.weight(1f)) { hvacManager.setAutoOn(!isAutoOn) }
            ClimateToggleButton("RECIRC", isRecircOn, Modifier.weight(1f)) { hvacManager.setRecircOn(!isRecircOn) }
            ClimateToggleButton("DEFROST", isFrontDefrostOn, Modifier.weight(1f)) { hvacManager.setFrontDefrostOn(!isFrontDefrostOn) }
            ClimateToggleButton("DUAL", isDualZoneOn, Modifier.weight(1f)) { hvacManager.setDualZoneOn(!isDualZoneOn) }
        }

        // =====================================================================
        // ROW 2B: Extended Climate Toggles (Max A/C, Max Def, Rear Def, Auto Recirc, Mirror & Electric Defrost)
        // =====================================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ClimateToggleButton("MAX A/C", isMaxAcOn, Modifier.weight(1f)) {
                hvacManager.setMaxAcOn(!isMaxAcOn)
            }
            ClimateToggleButton("MAX DEF", isMaxDefrostOn, Modifier.weight(1f)) {
                hvacManager.setMaxDefrostOn(!isMaxDefrostOn)
            }
            ClimateToggleButton("REAR DEF", isRearDefrostOn, Modifier.weight(1f)) {
                hvacManager.setRearDefrostOn(!isRearDefrostOn)
            }
            ClimateToggleButton("AUTO RECIRC", isAutoRecircOn, Modifier.weight(1f)) {
                hvacManager.setAutoRecircOn(!isAutoRecircOn)
            }
            ClimateToggleButton("MIRROR HEAT", isSideMirrorHeatOn, Modifier.weight(1f)) {
                hvacManager.setSideMirrorHeatOn(!isSideMirrorHeatOn)
            }
            ClimateToggleButton("ELEC DEF", isElectricDefrosterOn, Modifier.weight(1f)) {
                hvacManager.setElectricDefrosterOn(!isElectricDefrosterOn)
            }
        }

        // =====================================================================
        // ROW 2C: Steering Wheel Heat Control Card
        // =====================================================================
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Steering Wheel Heat: $steeringWheelHeat (${hvacManager.minSteeringWheelHeat}..${hvacManager.maxSteeringWheelHeat})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(
                        onClick = { hvacManager.setSteeringWheelHeat(steeringWheelHeat - 1) },
                        enabled = steeringWheelHeat > hvacManager.minSteeringWheelHeat,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Text("-", fontSize = 24.sp)
                    }
                    FilledTonalButton(
                        onClick = { hvacManager.setSteeringWheelHeat(steeringWheelHeat + 1) },
                        enabled = steeringWheelHeat < hvacManager.maxSteeringWheelHeat,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Text("+", fontSize = 24.sp)
                    }
                }
            }
        }

        // =====================================================================
        // ROW 3: Driving Safety & Distraction Restriction Banner
        // =====================================================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isDriving || !isParked) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isDriving || !isParked) "Driving Mode: Profile & Album settings locked"
                    else "Parked: Comfort profile & Album settings unlocked",
                    fontSize = 18.sp
                )
                Button(
                    onClick = { /* Open gallery or profile settings */ },
                    enabled = !isDriving && isParked,
                    modifier = Modifier.height(56.dp)
                ) {
                    Text("Edit Profile Photo", fontSize = 16.sp)
                }
            }
        }
    }
}

/**
 * Composable card for individual climate zone controls (Driver or Passenger),
 * including temperature adjustment, seat heating/cooling, and seat ventilation controls.
 */
@Composable
private fun ZoneControlCard(
    title: String,
    formattedTemp: String,
    rawTemp: Float,
    minTemp: Float,
    maxTemp: Float,
    seatLevel: Int,
    minSeat: Int,
    maxSeat: Int,
    seatVentLevel: Int,
    minSeatVent: Int,
    maxSeatVent: Int,
    showSeatVent: Boolean,
    onTempDecrease: () -> Unit,
    onTempIncrease: () -> Unit,
    onSeatDecrease: () -> Unit,
    onSeatIncrease: () -> Unit,
    onSeatVentDecrease: () -> Unit,
    onSeatVentIncrease: () -> Unit
) {
    Card(modifier = Modifier.width(290.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = title, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Text(text = formattedTemp, fontSize = 34.sp, fontWeight = FontWeight.Bold)

            // Temperature adjustment buttons
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FilledTonalButton(onClick = onTempDecrease, enabled = rawTemp > minTemp, modifier = Modifier.size(76.dp)) {
                    Text("-", fontSize = 28.sp)
                }
                FilledTonalButton(onClick = onTempIncrease, enabled = rawTemp < maxTemp, modifier = Modifier.size(76.dp)) {
                    Text("+", fontSize = 28.sp)
                }
            }

            HorizontalDivider()

            // Seat Heating / Cooling status and buttons
            val seatText = when {
                seatLevel > 0 -> "Seat Heat: +$seatLevel"
                seatLevel < 0 -> "Seat Cool: $seatLevel"
                else -> "Seat Temp: Off"
            }
            Text(text = seatText, fontSize = 16.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onSeatDecrease, enabled = seatLevel > minSeat, modifier = Modifier.height(52.dp)) {
                    Text("Cool -")
                }
                OutlinedButton(onClick = onSeatIncrease, enabled = seatLevel < maxSeat, modifier = Modifier.height(52.dp)) {
                    Text("Heat +")
                }
            }

            // Seat Ventilation Controls (if supported by vehicle hardware)
            if (showSeatVent || seatVentLevel > 0) {
                HorizontalDivider()
                Text(text = "Seat Vent: $seatVentLevel / $maxSeatVent", fontSize = 16.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onSeatVentDecrease, enabled = seatVentLevel > minSeatVent, modifier = Modifier.height(52.dp)) {
                        Text("Vent -")
                    }
                    OutlinedButton(onClick = onSeatVentIncrease, enabled = seatVentLevel < maxSeatVent, modifier = Modifier.height(52.dp)) {
                        Text("Vent +")
                    }
                }
            }
        }
    }
}

/**
 * Composable card for blower fan speed control, actual RPM readout, and airflow vent direction selection.
 */
@Composable
private fun FanAndAirflowCard(
    fanSpeed: Int,
    minSpeed: Int,
    maxSpeed: Int,
    actualRpm: Int,
    showRpm: Boolean,
    fanDirection: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onDirectionSelect: (Int) -> Unit
) {
    Card(modifier = Modifier.width(300.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = "Fan Speed", fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Text(text = "$fanSpeed / $maxSpeed", fontSize = 34.sp, fontWeight = FontWeight.Bold)

            // Actual Blower Fan RPM readout (if supported)
            if (showRpm || actualRpm > 0) {
                Text(
                    text = "Actual Blower: $actualRpm RPM",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            // Fan speed increment / decrement buttons
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FilledTonalButton(onClick = onDecrease, enabled = fanSpeed > minSpeed, modifier = Modifier.size(76.dp)) {
                    Text("-", fontSize = 28.sp)
                }
                FilledTonalButton(onClick = onIncrease, enabled = fanSpeed < maxSpeed, modifier = Modifier.size(76.dp)) {
                    Text("+", fontSize = 28.sp)
                }
            }

            HorizontalDivider()

            Text(text = "Airflow Vents", fontSize = 16.sp)
            // Airflow Direction Filter Chips (Face, Floor, Both)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = fanDirection == VehicleHvacFanDirection.FACE,
                    onClick = { onDirectionSelect(VehicleHvacFanDirection.FACE) },
                    label = { Text("Face") }
                )
                FilterChip(
                    selected = fanDirection == VehicleHvacFanDirection.FLOOR,
                    onClick = { onDirectionSelect(VehicleHvacFanDirection.FLOOR) },
                    label = { Text("Floor") }
                )
                FilterChip(
                    selected = fanDirection == (VehicleHvacFanDirection.FACE or VehicleHvacFanDirection.FLOOR),
                    onClick = { onDirectionSelect(VehicleHvacFanDirection.FACE or VehicleHvacFanDirection.FLOOR) },
                    label = { Text("Both") }
                )
            }
        }
    }
}

/**
 * Reusable toggle button component for primary and extended HVAC features (A/C, Auto, Recirc, Defrost, etc.).
 */
@Composable
private fun ClimateToggleButton(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(76.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (active) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Text(text = label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
