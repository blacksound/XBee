///An XBee device is a device which is physcially connected to the computer via a serial port
XBeeDevice {
	var <serialPort;
	var <>rxAction;
	var parseRoutine, parser;
	var <childDevices;
	var <responseActions;

	*new {arg serialPort;
		^super.new.init(serialPort);
	}

	init{ arg serialPort_;
		serialPort = serialPort_;
		childDevices = Dictionary.new;
		responseActions = IdentityDictionary.new;
		"XBeeAPI initialized".postln;
	}

	*parseStates{ ^this.subclassResponsibility; }
}

XBeeDeviceAPIMode : XBeeDevice {
	var frameIDResponseActions;
	var nextFrameID = 0;

	init{arg serialPort_;
		super.init(serialPort_);
		parser = XBeeAPIParser.new(this);
		frameIDResponseActions = Array.newClear(255);//frameID specific responders are stored here
	}

	nextFrameID {
		var oldFrameID = nextFrameID;
		nextFrameID = ((nextFrameID % 255) + 1);
		^oldFrameID;
	}

	sendRemoteTX{arg serialAddressArray, networkAddressArray, payload, options = 0;
		var frame, frameTypeByteCode;
		payload = [0x00, options, payload].flat; //radius, options and payload
		frameTypeByteCode = XBeeAPI.frameTypeByteCodes.at(\ZigBeeTransmitRequest);
		frame = this.frameRemoteCommand(serialAddressArray, networkAddressArray, frameTypeByteCode, payload);
		"NEW sent this: %".format(frame).postln;
		this.sendAPIFrame(frame);
	}

	sendRemoteATCommand{arg serialAddressArray, networkAddressArray, command, parameter;
		var frame, payload, frameTypeByteCode;
		payload = [0x02, command.ascii, parameter].flat.reject(_.isNil);//prepend 0x02 for apply changes
		frameTypeByteCode = XBeeAPI.frameTypeByteCodes.at(\RemoteCommandRequest);
		frame = this.frameRemoteCommand(serialAddressArray, networkAddressArray, frameTypeByteCode, payload);
		this.sendAPIFrame(frame);
	}

	sendLocalATCommand{arg command, parameter;
		var payload, frame;
		payload = [command.ascii, parameter].flat.reject(_.isNil);
		frame = this.frameCommand(XBeeAPI.frameTypeByteCodes.at(\ATCommand), payload);
		this.sendAPIFrame(frame);
	}

	frameRemoteCommand{arg serialAddressArray, networkAddressArray, frameType, payload;
		payload = [serialAddressArray, networkAddressArray, payload].flat;
		^this.frameCommand(frameType, payload);
	}

	frameCommand{arg frameType, payload;
		var frame, lengthMSB, lengthLSB, checksum, payloadLength, frameID;
		payloadLength = payload.size + 1 /*frameType*/ + 1 /*frameID*/;
		lengthMSB = payloadLength >> 8;
		lengthLSB = payloadLength.bitAnd(0xFF);
		frameID = this.nextFrameID;
		checksum = 0xFF - [frameType, frameID, payload].flat.sum.bitAnd(0xFF);
		frame = [
			XBeeAPI.startDelimiter,
			lengthMSB,
			lengthLSB,
			frameType,
			frameID,
			payload,
			checksum
		].flatten;
		^frame;
	}

	sendAPIFrame{arg frame;
		"sending frame: %".format(frame.collect(_.asHexString(2))).postln;
		serialPort.putAll(frame);
	}

	start{
		if((parseRoutine.isPlaying.not) or: (parseRoutine.isNil)) {
			parseRoutine = Routine({
				var byte, length, frameType;
				inf.do {
					byte = serialPort.read;
					rxAction.value(byte);
					parser.parseByte(byte);
				}
			}).play;
		}
		{
			"parse routine is already playing!".postln;
		}
	}

	stop {
		if((parseRoutine.notNil) and: (parseRoutine.isPlaying)) {
			parseRoutine.stop;
		}
	}
}

//A XBeeDeviceProxy is a XBee device connected via another XBee device's network.

//Abstract base class for XBee parser classes
XBeeParser{
	var state;
	var device;
	var parseFunctions;

	init{ ^this.subclassResponsibility; }

	parseByte{arg byte;
		parseFunctions.at(state).value(byte);
	}

	*prParseAddressBytes{arg bytes;
		var result = 0;
		bytes.reverseDo{arg item, i;
			result = result.bitOr(item << (i * 8));
		};
		^result;
	}
}

XBeeAPIParser : XBeeParser {
	var numLengthBytesReceived;
	var frameByteBuffer;
	var frameDataParseFunctions;
	var currentFrameType;
	var currentFrameID;
	var expectedFrameLength;
	var <>doChecksumVerification;

	*new{arg device;
		^super.new.init(device);
	}

	init{arg device_;
		device = device_;
		state = \waitingForStartDelimiter;
		frameByteBuffer = Array.new(128);//ZigBee protocol maximum packet size
		numLengthBytesReceived = 0;
		expectedFrameLength = 0;
		doChecksumVerification = false;

		parseFunctions = IdentityDictionary[
			\waitingForStartDelimiter -> {arg byte;
				if(byte == XBeeAPI.startDelimiter, {
					frameByteBuffer = Array.new(128);
					numLengthBytesReceived = 0;
					state = \waitingForLength;
				});
			},
			\waitingForLength -> {arg byte;
				switch(numLengthBytesReceived,
					0, {
						expectedFrameLength = byte << 8;
						numLengthBytesReceived = 1;
					},
					1, {
						expectedFrameLength = expectedFrameLength.bitOr(byte.bitAnd(0xFF));
						state = \waitingForFrameData;
					}
				)
			},
			\waitingForFrameData -> {arg byte;
				frameByteBuffer.add(byte);
				if(frameByteBuffer.size >= expectedFrameLength, {
					state = \waitingForChecksum;
				})
			},
			\waitingForChecksum -> {arg byte;
				if(doChecksumVerification,
					{/*do checksum test here*/ this.parseFrameBytes;},
					{this.parseFrameBytes;}
				);
				state = \waitingForStartDelimiter;
			}
		];

		frameDataParseFunctions = IdentityDictionary[
			/*\ATCommand -> {arg byte; currentFrameData.at()},
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
			\RemoteCommandResponser -> 0x97,
			\OverTheAirFirmwareUpdateStatus -> 0xA0,
			\RouteRecordIndicator -> 0xA1*/
		];
	}

	parseFrameBytes{
		var frameType, frameID;
		frameType = XBeeAPI.frameTypeByteCodes.getID(frameByteBuffer.first);
		frameID = frameByteBuffer[1];

		//we have store these in frameType and frameID, so we don't need them anymore. Reversing array for popping.
		frameByteBuffer = frameByteBuffer.drop(2).reverse;
		switch(frameType,
			\ZigBeeReceivePacket, {
				var address64Bit, networkAddr, receiveOptions, receivedData;
				address64Bit = this.class.prParseAddressBytes( { frameByteBuffer.pop } ! 8 );
				networkAddr = this.class.prParseAddressBytes( { frameByteBuffer.pop } ! 2 );
				receiveOptions = frameByteBuffer.pop;
				receivedData = frameByteBuffer.copy;
				"got a recived UART frame".postln;
				device.responseActions.at(\ZigBeeReceivePacket).value(address64Bit, networkAddr, receivedData);
			},\ZigBeeTransmitStatus, {
				var networkAddr, numRetries, deliveryStatus, discoveryStatus;
				networkAddr = this.class.prParseAddressBytes( { frameByteBuffer.pop } ! 2 );
				numRetries = frameByteBuffer.pop;
				deliveryStatus = frameByteBuffer.pop;
				discoveryStatus = frameByteBuffer.pop;
				"ZigBeeTransmitStatus: \n\tnetworkAddr: % \n\tnumRetries: % \n\tdeliveryStatus: % \n\tdiscoveryStatus: %".format(
					networkAddr, numRetries, deliveryStatus.asHexString(2), discoveryStatus.asHexString(2)
				).postln;
			}
		);
		"The buff:{%} %".format(XBeeAPI.frameTypeByteCodes.getID(frameByteBuffer.first), frameByteBuffer.collect(_.asHexString(2))).postln;
		"As chars: %".format(frameByteBuffer.collect(_.asAscii)).postln;
	}

}

XBeeAPI {
	classvar <atCommandCodes;
	classvar <frameTypeByteCodes;
	classvar <startDelimiter = 0x7E;
	classvar <broadcastSerialAddress;

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
			\DestinationAddressHigh -> 'DL',//CRE,
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



/// Maybe superfluous stuff:

// sendRemoteATCommand{arg serialAddressArray, networkAddressArray, command, parameter;
// 	var frame, payload, frameTypeCode;
// 	payload = [0x02, command.ascii, parameter].flat.reject(_.isNil);//prepend 0x02 for apply changes
// 	frameTypeCode = this.class.frameTypeByteCodes.at(\ZigBeeTransmitRequest);
// 	frame = this.frameRemoteCommand(serialAddressArray, networkAddressArray, frameTypeCode, payload);
// 	this.sendAPIFrame(frame);
// }
//
// sendLocalATCommand{arg command, parameter;
// 	var payload, frame;
// 	payload = [command.ascii, parameter].flat.reject(_.isNil);
// 	frame = this.frameCommand(this.class.localATCommand, payload);
// 	this.sendAPIFrame(frame);
// }
//
// frameRemoteCommand{arg serialAddressArray, networkAddressArray, frameType, payload;
// 	payload = [serialAddressArray, networkAddressArray, payload].flat;
// 	^this.frameCommand(frameType, payload);
// }
//
// frameCommand{arg frameType, payload;
// 	var frame, lengthMSB, lengthLSB, checksum, payloadLength, frameID;
// 	payloadLength = payload.size + 1 /*frameType*/ + 1 /*frameID*/;
// 	lengthMSB = payloadLength >> 8;
// 	lengthLSB = payloadLength.bitAnd(0xFF);
// 	frameID = this.getFrameID;
// 	checksum = 0xFF - [frameType, frameID, payload].flat.sum.bitAnd(0xFF);
// 	frame = [
// 		this.class.startDelimiter,
// 		lengthMSB,
// 		lengthLSB,
// 		frameType,
// 		frameID,
// 		payload,
// 		checksum
// 	].flatten;
// 	^frame;
// }
//


