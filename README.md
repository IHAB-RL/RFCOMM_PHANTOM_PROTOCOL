# RFCOMM_PHANTOM_PROTOCOL

## Normal Boot Sequence

1. continuously blue LED: Bluetooth-Device System Initialisation (~5 seconds)
2. slow blinking blue LED: Bluetooth-Device waits to activate the configuration mode (max. 7.5 seconds)
3. continuously red LED? ERROR: No Smartphone-MAC is stored! Restart Bluetooth-Device and activate configuration mode to store MAC (see "Configuration Mode")
4. very slow blinking green LED: Bluetooth-Device tries to connect to linked Smartphone

## Configuration Mode
Bluetooth-Device is able to store one Smartphone-MAC. If device is Smartphone-MAC stored, Bluetooth-Device will automatically connect to this Smartphone after boot sequence.  
1. (re)start Bluetooth device
2. in Smartphone Bluetooth Settings: establish pairing with Bluetooth-Device (e.g. with BC-09445B) (pairing is only possible in Steps 1-2 of normal boot sequence!)
3. (re)start Bluetooth device
4. when Bluetooth-Device waits for configuration mode (see 2. in "Normal Boot Sequence"): link to the Bluetooth-Device using IHAB-App oder IHABBluetoothAudio-App
7. In Configuration Mode blue LED will blink fast (max. 10 seconds), Smartphone-MAC will be stored automatically.
8. If Smartphone-MAC is stored, green LED blinks fast 5 times. Finally, normal boot sequence step 4 will start. If storing of Smartphone-MAC fails, blue LED will blink continuously. Please repeat step 6 quick OR step 3.

## Troubleshoot
**Datatransmission fails** 
1. Open the Android Settings App 
2. Tap "System"
3. Tap "Reset"
4. Tap "Restore Network Settings"
5. Confirm
