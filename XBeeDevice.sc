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
	var <childDeviceRoutes;
	var <>autoUpdateSourceRoutes = true;
	var <>zigBeeTransmitStatusAction;

	*new {arg serialPort;
		^super.new.init(serialPort);
	}

	init{ arg serialPort_;
		serialPort = serialPort_;
		childDevices = Dictionary.new;
		childDeviceRoutes = Dictionary.new;
		responseActions = IdentityDictionary.new;

		//Sending a dummy AT command to "prime" the connection. If seems to help to init problems where
		//the first AT command is not responded to.
		this.sendATCommand('SerialNumberHigh');

		this.getInitATValuesFromDevice;
	}

	getInitATValuesFromDevice{
		//these values are sent queued
		this.sendATCommand('SerialNumberLow', responseAction: {arg data; "A: %".format(data).postln;addressLo = XBeeParser.parseAddressBytes(data[\commandData])});
		this.sendATCommand('SerialNumberHigh', responseAction: {arg data; "B: %".format(data).postln;addressHi = XBeeParser.parseAddressBytes(data[\commandData])});
		//this.sendATCommand('NetworkAddress', responseAction: {arg data; "C: %".format(data).postln;networkAddress = XBeeParser.parseAddressBytes(data[\commandData])});
		//this.sendATCommand('NodeIdentifier', responseAction: {arg data;
		//	"D: %".format(data).postln;
		//	nodeIdentifier = String.newFrom(data[\dataBytes].collect(_.asAscii));
		//});
		//the last is sent as normal AT command, executing all queued commands on device

	}

	getChildDeviceByName{arg name;
		^childDevices.detect({arg addr; addr.nodeIdentifier == name});
	}

	sendATCommand{ this.subclassResponsibility;}
	sendQueuedATCommand{this.subclassResponsibility;}
	sendTransmitRequest{ this.subclassResponsibility; }
	sendExplicitAddrCmdFrame{ this.subclassResponsibility; }
	sendRemoteATCommandRequest{ this.subclassResponsibility; }
	createSourceRoute{ this.subclassResponsibility; }

	//overriden as empty method in endDevice ipmlementation
	prRegisterChildDevice{arg deviceType, sourceAddrHi, sourceAddrLo, sourceNetworkAddr, nodeIdentifier;
		var deviceClass, newDevice;
		//which means it tries to register itself
		"Comparing: %, % = %".format(sourceAddrLo, addressLo, sourceAddrLo == addressLo).postln;
		if(sourceAddrLo == addressLo, {
			"wont register itself".postln;
			^this;
		});
		if(childDevices.includesKey(sourceAddrLo).not, {
			deviceClass = switch(deviceType,
				\coordinator, {XBeeCoordinatorProxy},
				\router, {XBeeRouterProxy},
				\endDevice, {XBeeEndDeviceProxy}
			);
			newDevice = deviceClass.new(this, sourceAddrHi, sourceAddrLo, sourceNetworkAddr, nodeIdentifier);
			childDevices.put(newDevice.addressLo, newDevice);
			this.changed(\childDeviceRegistered, newDevice);
			this.sendATCommand(\AggregateRoutingNotification, [0]);
			"Device % % % joined network".format(newDevice.nodeIdentifier, newDevice.networkAddress, newDevice.addressLo).postln;
		}, {
				//update remote XBee data as this might have changed
				newDevice = childDevices.at(sourceAddrLo);
				newDevice.networkAddress_(sourceNetworkAddr);
				newDevice.nodeIdentifier_(nodeIdentifier);
				this.sendATCommand(\AggregateRoutingNotification, [0]);
				newDevice.prRemoteTransmissionStatus(true);
				"Device % % % already joined".format(nodeIdentifier, sourceNetworkAddr, sourceAddrLo).postln;
		});
	}
}


XBeeDeviceAPIMode : XBeeDevice {
	var frameIDResponseActions;
	var nextFrameID = 0;

	init{arg serialPort_;
		frameIDResponseActions = Array.newClear(255);//frameID specific responders are stored here
		parser = XBeeAPIParser.new(this);
		this.start;
		super.init(serialPort_);
	}

	sendATCommand{arg cmd, parameterBytes, responseAction;
		var frame, cmdBytes, payload;
		cmdBytes = XBeeAPI.atCommandCodes[cmd].ascii;
		cmdBytes ?? {
			"Unknown AT command: %".format(cmd).error.throw;
		};
		frame = this.frameCommand(
			XBeeAPI.frameTypeByteCodes.at(\ATCommand),
			(cmdBytes ++ parameterBytes).flat,
			responseAction: responseAction
		);
		this.sendAPIFrame(frame);
	}

	sendQueuedATCommand{arg cmd, parameterBytes, responseAction;
		var frame, cmdBytes;
		cmdBytes = XBeeAPI.atCommandCodes[cmd].ascii;
		cmdBytes ?? {
			"Unknown AT command: %".format(cmd).error.throw;
		};
		frame = this.frameCommand(
			XBeeAPI.frameTypeByteCodes.at(\ATCommandQueued),
			cmdBytes ++ parameterBytes,
			responseAction: responseAction
		);
		//"Queued AT frame: %".format(frame).postln;
		this.sendAPIFrame(frame);
	}

	sendRemoteATCommandRequest{arg addressHi, addressLo, networkAddress = 0xFFFE, cmd, parameterBytes, responseAction;
		var frame, cmdBytes;
		cmdBytes = XBeeAPI.atCommandCodes[cmd].ascii;
		cmdBytes ?? {
			"Unknown AT command: %".format(cmd).error.throw;
		};
		frame = this.frameRemoteCommand(
			addressHi, addressLo, networkAddress,
			XBeeAPI.frameTypeByteCodes.at(\RemoteCommandRequest),
			[0x02]/*apply changes*/ ++ cmdBytes ++ parameterBytes,
			responseAction: responseAction
		);
		this.sendAPIFrame(frame);
	}

	sendTransmitRequest{
		arg addressHi, addressLo, networkAddress = 0xFFFE, payload,
		sendFrameID, broadcastRadius = 0, options = 0;
		var frame;
		frame = this.frameRemoteCommand(
			addressHi, addressLo, networkAddress,
			XBeeAPI.frameTypeByteCodes[\ZigBeeTransmitRequest],
			[broadcastRadius, options] ++ payload,
			sendFrameID
		);
		this.sendAPIFrame(frame)
	}
	sendExplicitAddrCmdFrame{}


	createSourceRoute{arg sourceAddressLo, sourceNetworkAddress, networkRoute;
		var payload, routeBytes;
		payload = XBeeParser.unpackAddressBytes(XBeeAPI.defaultSerialAddressHigh, 4);
		payload = payload ++ XBeeParser.unpackAddressBytes(sourceAddressLo, 4);
		payload = payload ++ XBeeParser.unpackAddressBytes(sourceAddressLo, 2);
		payload = payload ++ [0];
		if(networkRoute.size == 0,
			{
				payload = payload ++ [0];
				//payload = payload ++ [0,0];
			},
			{
				payload = payload ++ networkRoute.size;
				payload = payload ++ networkRoute.collect({arg item;
					XBeeParser.unpackAddressBytes(item, 2);
				}).flat;
			}
		);
		// "Creating source route: %, %, %".format(sourceAddressLo, sourceNetworkAddress, networkRoute).postln;
		// "Payload: %".format(payload).postln;
		this.frameCommand(
			frameType: XBeeAPI.frameTypeByteCodes[\CreateSourceRoute],
			payload: payload,
			sendFrameID: false
		);
	}

	nextFrameID {
		var oldFrameID = nextFrameID;
		nextFrameID = ((nextFrameID % 255) + 1);
		^oldFrameID;
	}

	frameRemoteCommand{arg addressHi, addressLo, networkAddress, frameType, payload, sendFrameID, responseAction;
		var addrHigh, netAddr;
		if(addressHi.isNil, {
			addrHigh = XBeeAPI.defaultSerialAddressHigh;
		}, {addrHigh = XBeeParser.unpackAddressBytes(addressHi, 4)});
		if(networkAddress.isNil, {
			netAddr = 0xFFFE;
		}, {netAddr = XBeeParser.unpackAddressBytes(networkAddress, 2)});
		payload = [
			addrHigh,
			XBeeParser.unpackAddressBytes(addressLo, 4),
			netAddr,
			payload].flat;
		^this.frameCommand(frameType, payload, sendFrameID, responseAction);
	}

	frameCommand{arg frameType, payload, sendFrameID, responseAction;
		var frame, lengthMSB, lengthLSB, checksum, payloadLength, frameID;
		sendFrameID = sendFrameID ? true;
		payloadLength = payload.size + 1 /*frameType*/ + 1 /*frameID*/;
		lengthMSB = payloadLength >> 8;
		lengthLSB = payloadLength.bitAnd(0xFF);
		frameID = if(sendFrameID or: {responseAction.notNil}, {this.nextFrameID}, {0});
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
		responseAction !? {
			"putting response action: %, %".format(responseAction, frameID).postln;
			frameIDResponseActions.put(frameID, responseAction);
		};
		^frame;
	}

	sendAPIFrame{arg frame;
		//"sending frame: %".format(frame.collect(_.asHexString(2))).postln;
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
				this.prRegisterChildDevice(
					deviceType: #[\coordinator, \router, \endDevice].at(frameData[\deviceType]),
					sourceAddrHi: frameData[\sourceAddressHi],
					sourceAddrLo: frameData[\sourceAddressLo],
					sourceNetworkAddr: frameData[\sourceNetworkAddress],
					nodeIdentifier: frameData[\nodeIdentifier]
				);
			},
			\ZigBeeReceivePacket, {
				var childDevice = childDevices.at(frameData[\sourceAddressLo]);
				childDevice !? {childDevice.rxAction.value(frameData[\data])};
				//"UART data:".postln;
			},
			\RouteRecordIndicator, {
				//"Got route Record Indicator: [%] %".format(frameData[\sourceAddressLo], frameData[\networkRoute]).postln;
				//printAddress.value;
				childDeviceRoutes.put(frameData[\sourceAddressLo], frameData[\networkRoute]);
				if(autoUpdateSourceRoutes, {
					this.createSourceRoute(frameData[\sourceAddressLo], frameData['sourceNetworkAddress'], frameData[\networkRoute]);
				});
			},
			\ATCommandResponse, {
				if(frameData['ATCommand'] == 'ND' and: {frameData[\commandStatus] == 'OK'}, {
					var newNodeData;
					newNodeData = XBeeParser.parseNodeDiscoverResponse(frameData[\commandData]);
					//"NewNodeData: \t%".format(newNodeData).postln;
					this.prRegisterChildDevice(
						deviceType: #[\coordinator, \router, \endDevice].at(newNodeData[\deviceType]),
						sourceAddrHi: newNodeData[\sourceAddressHi],
						sourceAddrLo: newNodeData[\sourceAddressLo],
						sourceNetworkAddr: newNodeData[\sourceNetworkAddress],
						nodeIdentifier: newNodeData[\nodeIdentifier]
					);
				});
				if(frameData['frameID'] > 0, {
					this.prDoResponseAction(frameData['frameID'], frameData);
				});
				"ATcommand response: %".format(frameData).postln;
			},
			\ZigBeeTransmitStatus, {
				zigBeeTransmitStatusAction.value(frameData);
			},
			\ModemStatus, {
				this.changed(\modemStatus, frameData);
			},
			\RemoteCommandResponse, {
				childDevices.at(frameData.at(\sourceAddressLo)).prGotCommandResponse(frameData);
				if(frameData['frameID'] > 0, {
					this.prDoResponseAction(frameData['frameID'], frameData);
				});
			}
		);
	}

	prDoResponseAction{arg frameID, frameData;
		"frameIDResponseActions: %".format(frameIDResponseActions).postln;
		frameIDResponseActions.at(frameID).value(frameData);
		frameIDResponseActions.put(frameID, nil);
	}
}

XBeeCoordinatorAPIMode : XBeeDeviceAPIMode {

}


XBeeEndDeviceAPIMode : XBeeDeviceAPIMode {
	prRegisterChildDevice{}
}