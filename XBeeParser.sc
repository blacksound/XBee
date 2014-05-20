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
		frameByteBuffer;//ZigBee protocol maximum packet size
		numLengthBytesReceived = 0;
		expectedFrameLength = 0;
		doChecksumVerification = false;

		parseFunctions = IdentityDictionary[
			\waitingForStartDelimiter -> {arg byte;
				if(byte == XBeeAPI.startDelimiter, {
					frameByteBuffer = LinkedList.new(128);
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
						numLengthBytesReceived = 0;
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
			\RemoteCommandResponser -> 0x97,
			\OverTheAirFirmwareUpdateStatus -> 0xA0,
			\RouteRecordIndicator -> 0xA1
		];
	}

	parseFrameBytes{
		var parseAddress;
		var frameType, frameData = IdentityDictionary.new, frameByte, parseSuccess = false;
		parseAddress = {arg coll;
			coll.put(\sourceAddressHi, this.class.prParseAddressBytes({ frameByteBuffer.popFirst } ! 4));
			coll.put(\sourceAddressLo, this.class.prParseAddressBytes({ frameByteBuffer.popFirst } ! 4));
			coll.put(\sourceNetworkAddress, this.class.prParseAddressBytes({ frameByteBuffer.popFirst } ! 2));
		};
		frameByte = frameByteBuffer.popFirst;
		frameType = XBeeAPI.frameTypeByteCodes.getID(frameByte);
		switch(frameType,
			\ZigBeeReceivePacket, {
				parseAddress.value(frameData);
				frameData.put(\receiveOptions, frameByteBuffer.popFirst);
				frameData.put(\data, frameByteBuffer.asArray);
				parseSuccess = true;
			},/*\ZigBeeTransmitStatus, {
			var networkAddr, numRetries, deliveryStatus, discoveryStatus;
			networkAddr = this.class.prParseAddressBytes( { frameByteBuffer.pop } ! 2 );
			numRetries = frameByteBuffer.pop;
			deliveryStatus = frameByteBuffer.pop;
			discoveryStatus = frameByteBuffer.pop;
			"ZigBeeTransmitStatus: \n\tnetworkAddr: % \n\tnumRetries: % \n\tdeliveryStatus: % \n\tdiscoveryStatus: %".format(
			networkAddr, numRetries, deliveryStatus.asHexString(2), discoveryStatus.asHexString(2)
			).postln;
			},
			*/
			\NodeIdentificationIndicator, {
				parseAddress.value(frameData);
				frameData.put(\receiveOptions, frameByteBuffer.popFirst);
				frameData.put(\remoteNetworkAddress, this.class.prParseAddressBytes( { frameByteBuffer.popFirst } ! 2 ));
				frameData.put(\remoteAddressHi, this.class.prParseAddressBytes( { frameByteBuffer.popFirst } ! 4 ));
				frameData.put(\remoteAddressLo, this.class.prParseAddressBytes( { frameByteBuffer.popFirst } ! 4 ));
				frameData.put(\nodeIdentifier, String.newFrom(this.popFrameBytesUntilNull.collect(_.asAscii)));
				frameData.put(\parentNetworkAddr, this.class.prParseAddressBytes({frameByteBuffer.popFirst} ! 2));
				frameData.put(\deviceType, frameByteBuffer.popFirst);
				frameData.put(\sourceEvent, frameByteBuffer.popFirst);
				frameData.put(\digiProfileID, {frameByteBuffer.popFirst} ! 2);
				frameData.put(\manufacturerID, {frameByteBuffer.popFirst} ! 2);
				parseSuccess = true;
			},
			\ATCommandResponse, {
				var commandStatus;
				frameData.put(\frameID, frameByteBuffer.popFirst);
				frameData.put('ATCommand', String.newFrom({frameByteBuffer.popFirst.asAscii} ! 2).asSymbol);
				commandStatus = #['OK', 'ERROR', 'InvalidCommand',
					'InvalidParameter', 'TxFailure'].at(frameByteBuffer.popFirst);
				frameData.put('commandStatus', commandStatus);
				frameData.put('commandData', frameByteBuffer.asArray);
				parseSuccess = true;
			}
		);
		"The rest: %".format(frameByteBuffer).postln;
		//		"As chars: %".format(frameByteBuffer.collect(_.asAscii)).postln;
		if(parseSuccess, {
			device.prGotAPIFrame(frameType, frameData);
		}, {
				"Frame type unknown: %".format(frameByte).warn;
				"\tFrame byte buffer: % ".format(frameByteBuffer).postln;
		})

	}

	popFrameBytesUntilNull{
		var result, nullFound = false;
		while({nullFound.not}, {
			var val = frameByteBuffer.popFirst;
			if(val == 0, {
				nullFound = true;
			}, {result = result.add(val);});
		});
		^result;
	}

}