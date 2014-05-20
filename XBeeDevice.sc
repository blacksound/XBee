///An XBee device is a device which is physcially connected to the computer via a serial port
XBeeDevice {
	var serialPort;
	var <addressHi;
	var <addressLo;
	var <networkAddress;
	var <nodeIdentifier;
	var <panID;
	var <>rxAction;
	var parseRoutine, parser;
	var <childDevices;
	var <responseActions;

	classvar <initATCommands;

	*initClass{
		initATCommands = [
			'NetworkAddress', 'SerialNumberHigh', 'SerialNumberLow', 'NodeIdentifier'
		];
	}

	*new {arg serialPort;
		^super.new.init(serialPort);
	}

	init{ arg serialPort_;
		serialPort = serialPort_;
		childDevices = Dictionary.new;
		responseActions = IdentityDictionary.new;
		this.getATValuesFromDevice;
	}

	getATValuesFromDevice{
		this.class.initATCommands.do({arg cmd, i;
			if(cmd != this.class.initATCommands.last, {
				this.sendQueuedATCommand(cmd);
			}, {
					this.sendATCommand(cmd);
			});
		});
	}

	sendATCommand{ this.subclassResponsibility;}
	sendQueuedATCommand{this.subclassResponsibility;}
	sendTransmitRequest{ this.subclassResponsibility; }
	sendExplicitAddrCmdFrame{ this.subclassResponsibility; }
	sendRemoteATCommandRequest{ this.subclassResponsibility; }
	createSourceRoute{ this.subclassResponsibility; }
}


XBeeDeviceAPIMode : XBeeDevice {
	var frameIDResponseActions;
	var nextFrameID = 0;

	init{arg serialPort_;
		super.init(serialPort_);
		parser = XBeeAPIParser.new(this);
		frameIDResponseActions = Array.newClear(255);//frameID specific responders are stored here
	}

	sendATCommand{arg cmd...parameterBytes;
		var frame, cmdBytes, payload;
		cmdBytes = XBeeAPI.atCommandCodes[cmd].ascii;
		cmdBytes ?? {
			"Unknown AT command: %".format(cmd).error.throw;
		};
		frame = this.frameCommand(XBeeAPI.frameTypeByteCodes.at(\ATCommand), cmdBytes ++ parameterBytes);
		"AT frame: %".format(frame).postln;
		this.sendAPIFrame(frame);
	}

	sendQueuedATCommand{arg cmd...parameterBytes;
		var frame, cmdBytes;
		cmdBytes = XBeeAPI.atCommandCodes[cmd].ascii;
		cmdBytes ?? {
			"Unknown AT command: %".format(cmd).error.throw;
		};
		frame = this.frameCommand(XBeeAPI.frameTypeByteCodes.at(\ATCommandQueued), cmdBytes ++ parameterBytes);
		"Queued AT frame: %".format(frame).postln;
		this.sendAPIFrame(frame);
	}

	sendTransmitRequest{}
	sendExplicitAddrCmdFrame{}
	sendRemoteATCommandRequest{}
	createSourceRoute{}

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

	prGotAPIFrame{arg frameType, frameData;
		var printAddress = {
			"Address: % - % networkAddr: %".format(
				frameData[\sourceAddressHi].asHexString,
				frameData[\sourceAddressLo].asHexString,
				frameData[\sourceNetworkAddress].asHexString).postln;
		};
		switch(frameType,
			\NodeIdentificationIndicator, {
				var deviceType;
				printAddress.value;
				deviceType = #[\coordinator, \router, \endDevice].at(frameData[\deviceType]);
				this.prRegisterChildDevice( deviceType,
					frameData[\sourceAddressHi], frameData[\sourceAddressLo],
					frameData[\sourceNetworkAddress], frameData[\nodeIdentifier]
				);
			},
			\ZigBeeReceivePacket, {"UART data:".postln;}
		);
		"\tFrame data".postln;
		frameData.keysValuesDo({arg key,val;
			if(val.isString.not and: {val.class != Symbol}, {
				if(val.isArray, {
					val = val.collect(_.asHexString);
					}, {
						val = val.asHexString;
				});
			});
			"\t[%]: %".format(key,val).postln;
		});
	}
}

XBeeCoordinatorAPIMode : XBeeDeviceAPIMode {
	prRegisterChildDevice{arg deviceType, sourceAddrHi, sourceAddrLo, sourceNetworkAddr, nodeIdentifier;
		var deviceClass, newDevice;
		deviceClass = switch(deviceType,
			\coordinator, {XBeeCoordinatorProxy},
			\router, {XBeeRouterProxy},
			\endDevice, {XBeeEndDeviceProxy}
		);
		newDevice = deviceClass.new(this, sourceAddrHi, sourceAddrLo, sourceNetworkAddr, nodeIdentifier);
		childDevices.put(newDevice.addressLo, newDevice);
		this.changed(\childDeviceRegistered, newDevice);
		"Device % % % joined network".format(newDevice.nodeIdentifier, newDevice.networkAddress, newDevice.addressLo).postln;
	}
}
