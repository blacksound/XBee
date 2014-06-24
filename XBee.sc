XBeeAPI {
	classvar <atCommandCodes;
	classvar <frameTypeByteCodes;
	classvar <startDelimiter = 0x7E;
	classvar <broadcastSerialAddress;
	classvar <xbeeDefaultSerialAddressHi = 0x0013A200;

	*initClass {
		broadcastSerialAddress = [0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF, 0xFF];
		frameTypeByteCodes = TwoWayIdentityDictionary[
			\ATCommand -> 0x08,
			\ATCommandQueued -> 0x09,
			\ZigBeeTransmitRequest -> 0x10,
			\ExplicitAddressingZigBeeCommandFrame -> 0x11,
			\RemoteCommandRequest -> 0x17,
			\CreateSourceRoute -> 0x21,
			\ATCommandResponse -> 0x88,
			\ModemStatus -> 0x8A,
			\ZigBeeTransmitStatus -> 0x8B,
			\ZigBeeReceivePacket -> 0x90,
			\ZigBeeExplicitRxIndicator -> 0x91,
			\ZigBeeIODataSampleIndicator -> 0x92,
			\XBeeSensorReadIndicator -> 0x94,
			\NodeIdentificationIndicator -> 0x95,
			\RemoteCommandResponse -> 0x97,
			\OverTheAirFirmwareUpdateStatus -> 0xA0,
			\RouteRecordIndicator -> 0xA1
		];
		atCommandCodes = TwoWayIdentityDictionary[
			//Addressing commands
			\DestinationAddressHigh -> 'DH',//CRE
			\DestinationAddressLow -> 'DL',//CRE,
			\NetworkAddress -> 'MY',//CRE
			\ParentNetworkAddress -> 'MP',//E
			\NumberOfRemainingChildren -> 'NC',//CR
			\SerialNumberHigh -> 'SH',//CRE
			\SerialNumberLow -> 'SL',//CRE
			\NodeIdentifier -> 'NI',//CRE
			\DeviceTypeIdentifier -> 'DD',//CRE
			\SourceEndpoint -> 'SE',//Only supported in AT firmware
			\DestinationEndpoint -> 'DE',//Only supported in AT firmware
			\ClusterIdentifier -> 'CI',//Only supported in AT firmware
			\MaximumRFPayloadBytes -> 'NP',
			//Networking commands
			\OperatingChannel -> 'CH',
			\ExtendedPanID -> 'ID',
			\OperatingExtendingPanID -> 'OP',
			\MaximumUnicastHops -> 'NH',
			\BroadcastHops -> 'BH',
			\Operating16BitPanID -> 'OI',
			\NodeDiscoverTimeout -> 'NT',
			\NetworkDiscoveryOptions -> 'NO',
			\ScanChannels -> 'SC',
			\ScanDuration -> 'SD',
			\ZigBeeStackProfile -> 'ZS',
			\NodeJoinTime -> 'NJ',//CR
			\ChannelVerification -> 'JV',//R
			\JoinNotification -> 'JN',//RE
			\AggregateRoutingNotification -> 'AR',//CR
			\AssociationIndication -> 'AI',
			//Security commands
			\EncryptionEnable -> 'EE',
			\EncryptionOptions -> 'EO',
			\NetworkEncryptionKey -> 'NK',//C
			\LinkKey -> 'KY',
			//RF Interfacing commands
			\PowerLevel -> 'PL',
			\PowerMode -> 'PM',
			\ReceivedSignalStrength -> 'DB',
			\APIEnable -> 'AP',
			\APIOptions -> 'AO',
			\InterfaceBaudrate -> 'DB',
			\SerialParity -> 'NB',
			\PacketizationTimeout -> 'RO',
			//Diagnostics commands
			\FirmwareVersion -> 'VR',
			\HardwareVersion -> 'HV',
			//AT command options
			\CommandModeTimeout -> 'CT',
			\ExitCommandMode -> 'CN',
			\GuardTimes -> 'GT',
			\CommandSequenceCharacter -> 'CC',
			//Sleep commands
			\SleepMode -> 'SM',
			\NumberOfSleepPeriods -> 'SN',
			\SleepPeriod -> 'SP',
			\TimeBeforeSleep -> 'ST',
			\SleepOptions -> 'SO',
			\WakeHost -> 'WH',
			//Execution commands
			\ApplyChanges -> 'AC',
			\Write -> 'WR',
			\RestoreDefaults -> 'RD',
			\SoftwareReset -> 'FR',
			\NetworkReset -> 'NR',
			\SleepImmediately -> 'SI',
			\NodeDiscover -> 'ND',
			\DestinationNode -> 'DN',
			\ForceSample -> 'SI',
			\XBeeSensorsample -> '1S',

			//IO commands
			\IOSampleRate -> 'IO',
			\IODigitalChangeDetection -> 'IC',
			\AssocLEDBlinkTime -> 'LT',
			\InternalPullUpBitfield -> 'PR',
			\RSSIPWMTimer -> 'RP',
			\ComissioningPushbutton -> 'CB',
			\SupplyVoltage -> '%V',

			//Pin configuration commmands
			//PWM0:
			// 0 = Disabled
			// 1 = RSSI PWM
			// 3 - Digital input, monitored
			// 4 - Digital output, default low
			// 5 - Digital output, default high
			\PWM0Configuration -> 'P0',

			//DIO11:
			// 0 - Unmonitored digital input
			// 3- Digital input, monitored
			// 4- Digital output, default low
			// 5- Digital output, default high
			\DIO11Configuration -> 'P1',

			//DIO12:
			// 0 - Unmonitored digital input
			// 3- Digital input, monitored
			// 4- Digital output, default low
			// 5- Digital output, default high
			\DIO12Configuration -> 'P2',

			//DIO13:
			// 0 – Disabled
			// 3 – Digital input
			// 4 – Digital output low
			// 5 – Digital output, high
			\DIO13Configuration -> 'P3',

			//AD0
			// 1 - Commissioning button enabled
			// 2 - Analog input, single ended
			// 3 - Digital input
			// 4 - Digital output, low
			// 5 - Digital output, high
			\AD0Configuration -> 'D0',

			//AD1
			// 0 – Disabled
			// 2 - Analog input, single ended
			// 3 – Digital input
			// 4 – Digital output, low
			// 5 – Digital output, high
			\AD1Configuration -> 'D1',

			//AD2
			// 0 – Disabled
			// 2 - Analog input, single ended
			// 3 – Digital input
			// 4 – Digital output, low
			// 5 – Digital output, high
			\AD2Configuration -> 'D2',

			//AD3
			// 0 – Disabled
			// 2 - Analog input, single ended
			// 3 – Digital input
			// 4 – Digital output, low
			// 5 – Digital output, high
			\AD3Configuration -> 'D3',

			//AD4
			// 0 – Disabled
			// 3 – Digital input
			// 4 – Digital output, low
			// 5 – Digital output, high
			\AD4Configuration -> 'D4',

			//AD5
			// 0 = Disabled
			// 1 = Associated indication LED
			// 3 = Digital input
			// 4 = Digital output, default low
			// 5 = Digital output, default high
			\AD5Configuration -> 'D5',

			//DIO6
			// 0 = Disabled
			// 1 = RTS flow control
			// 3 = Digital input
			// 4 = Digital output,
			// low 5 = Digital output, high
			\DIO6Configuration -> 'D6',

			//DIO7
			// 0 = Disabled
			// 1 = CTS Flow Control
			// 3 = Digital input
			// 4 = Digital output,low
			// 5 = Digital output,high
			// 6 = RS-485 transmit enable (low enable), 7 = RS-485 transmit enable (high enable)
			\DIO7Configuration -> 'D7',

			//DIO8
			// 0 – Disabled
			// 3 – Digital input
			// 4 – Digital output, low
			// 5 – Digital output, high
			\DIO8Configuration -> 'D8'
		];
	}
}