//Abstract base class for XBee parser classes
XBeeParser{
	var state;
	var device;
	var parseFunctions;

	init{ ^this.subclassResponsibility; }

	parseByte{arg byte;
		parseFunctions.at(state).value(byte);
	}

	*parseAddressBytes{arg bytes;
		var result = 0;
		bytes.reverseDo{arg item, i;
			result = result.bitOr(item << (i * 8));
		};
		^result;
	}

	*unpackAddressBytes{arg address, numBytes;
		var result;
		numBytes.reverseDo({arg i;
			result = result.add((address >> (i*8)) bitAnd: 0xFF);
		});
		^result;
	}

	*parseNodeDiscoverResponse{arg bytes;
		var result = IdentityDictionary.new;
		var byteList = bytes.iter, currentByte, idString;
		result.put(\sourceNetworkAddress, this.parseAddressBytes({byteList.next} ! 2));
		result.put(\sourceAddressHi, this.parseAddressBytes({byteList.next} ! 4));
		result.put(\sourceAddressLo, this.parseAddressBytes({byteList.next} ! 4));
		currentByte = byteList.next;
		while({currentByte.notNil and: {currentByte != 0}}, {
			idString = idString.add(currentByte.asAscii);
			currentByte = byteList.next;
		});
		if(idString.notNil, {
			result.put(\nodeIdentifier, String.newFrom(idString));
		});
		result.put(\parentNetworkAddress, this.parseAddressBytes({byteList.next} ! 2));
		result.put(\deviceType, byteList.next);
		result.put(\profileID, {byteList.next} ! 2);
		result.put(\manufacturerID, {byteList.next} ! 2);
		^result;
	}

	*parseNullTerminatedStringFromByteStream{arg byteStream;
		var result, currentByte;
		currentByte = byteStream.next;
		while({currentByte.notNil and: {currentByte != 0}}, {
			result = result.add(currentByte.asAscii);
			currentByte = byteStream.next;
		});
		^String.newFrom(result);
	}

	*popFrameBytesUntilNull{arg bytes; //expects a LinkedList
		var result, nullFound = false;
		while({nullFound.not}, {
			var val = bytes.popFirst;
			if(val == 0, {
				nullFound = true;
			}, {result = result.add(val);});
		});
		^result;
	}

}

XBeeAPIParser : XBeeParser {
	var numLengthBytesReceived;
	var frameByteBuffer;
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
		numLengthBytesReceived = 0;
		expectedFrameLength = 0;
		doChecksumVerification = false;

		parseFunctions = IdentityDictionary[
			\waitingForStartDelimiter -> {arg byte;
				if(byte == XBeeAPI.startDelimiter, {
					frameByteBuffer = Array.new(128);//ZigBee protocol maximum packet size
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
				frameByteBuffer = frameByteBuffer.add(byte);
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
	}

	parseFrameBytes{
		var parseAddress, bufferStream, getRemainingBytes;
		var frameType, frameData = IdentityDictionary.new, frameByte, parseSuccess = false;
		bufferStream = frameByteBuffer.iter;
		parseAddress = {arg coll;
			coll.put(\sourceAddressHi, this.class.parseAddressBytes(bufferStream.nextN(4)));
			coll.put(\sourceAddressLo, this.class.parseAddressBytes(bufferStream.nextN(4)));
			coll.put(\sourceNetworkAddress, this.class.parseAddressBytes(bufferStream.nextN(2)));
		};
		getRemainingBytes = {arg stream;
			var result;
			stream.do{arg item; result = result.add(item);};
			result;
		};
		frameByte = bufferStream.next;
		frameType = XBeeAPI.frameTypeByteCodes.getID(frameByte);
		//"Parsing frame type: %".format(frameType).postln;
		switch(frameType,
			\ZigBeeReceivePacket, {
				var data;
				parseAddress.value(frameData);
				frameData.put(\receiveOptions, bufferStream.next);
				bufferStream.do{arg item; data = data.add(item);};
				frameData.put(\data, data);
				parseSuccess = true;
			},
			\ZigBeeTransmitStatus, {
				frameData.put(\frameID, bufferStream.next);
				frameData.put(\destinationAddress, this.class.parseAddressBytes( bufferStream.nextN(2) ));
				frameData.put(\retryCount, bufferStream.next);
				frameData.put(\deliveryStatus, bufferStream.next);
				frameData.put(\discoveryStatus, bufferStream.next);
				parseSuccess = true;
			},
			\NodeIdentificationIndicator, {
				parseAddress.value(frameData);
				frameData.put(\receiveOptions, bufferStream.next);
				frameData.put(\remoteNetworkAddress, this.class.parseAddressBytes( bufferStream.nextN(2) ));
				frameData.put(\remoteAddressHi, this.class.parseAddressBytes( bufferStream.nextN(4)));
				frameData.put(\remoteAddressLo, this.class.parseAddressBytes( bufferStream.nextN(4)));
				frameData.put(\nodeIdentifier, String.newFrom(this.class.parseNullTerminatedStringFromByteStream(bufferStream).collect(_.asAscii)));
				frameData.put(\parentNetworkAddr, this.class.parseAddressBytes( bufferStream.nextN(2) ));
				frameData.put(\deviceType, bufferStream.next);
				frameData.put(\sourceEvent, bufferStream.next);
				frameData.put(\digiProfileID, bufferStream.nextN(2) );
				frameData.put(\manufacturerID, bufferStream.nextN(2) );
				parseSuccess = true;
			},
			\ATCommandResponse, {
				var commandStatus;
				frameData.put(\frameID, bufferStream.next);
				frameData.put('ATCommand', String.newFrom( bufferStream.nextN(2).collect(_.asAscii) ).asSymbol);
				commandStatus = #['OK', 'ERROR', 'InvalidCommand',
					'InvalidParameter', 'TxFailure'].at(bufferStream.next);
				frameData.put('commandStatus', commandStatus);
				frameData.put('commandData', getRemainingBytes.value(bufferStream));
				parseSuccess = true;
			},
			\RouteRecordIndicator, {
				if(frameByteBuffer.size < 13,
					{
						"Unknown route record indicator:%".format(frameByteBuffer).postln;
						parseSuccess = false;
					},
					{
						frameData.put(\sourceAddressHi, this.class.parseAddressBytes( bufferStream.nextN(4) ));
						frameData.put(\sourceAddressLo, this.class.parseAddressBytes( bufferStream.nextN(4) ));
						frameData.put(\sourceNetworkAddress, this.class.parseAddressBytes( bufferStream.nextN(2) ));
						frameData.put(\receiveOptions,
							#['PacketAcknownledged', 'BroadcastPacket'].at(bufferStream.next)
						);
						frameData.put(\networkRoute, bufferStream.next.collect{arg i;
							this.class.parseAddressBytes( bufferStream.nextN(2) );
						});
						parseSuccess = true;
					}
				);
			},
			\ModemStatus, {
				var status;
				status = Dictionary[
					0 -> \hardwareReset, 1 -> \watchdogTimerReset, 2 -> \joinedNetwork, 3 -> \disassociated,
					6 -> \coordinatorStarted, 7 -> \networkSecurityKeyupdated, 13 -> \voltageSupplyLimitExceeded,
					17 -> \modemConfigChangedWhileJoinInProcess, {|it|it >= 128} -> \stackError
				].matchAt(bufferStream.next);
				frameData.put(\status, status ? \unknown);
				parseSuccess = true;
			},
			\RemoteCommandResponse, {
				frameData.put(\frameID, bufferStream.next);
				frameData.put(\sourceAddressHi, this.class.parseAddressBytes( bufferStream.nextN(4) ));
				frameData.put(\sourceAddressLo, this.class.parseAddressBytes( bufferStream.nextN(4) ));
				frameData.put(\sourceNetworkAddress, this.class.parseAddressBytes(  bufferStream.nextN(2) ));
				frameData.put(\ATCommand, String.newFrom( bufferStream.nextN(2).collect(_.asAscii) ).asSymbol);
				frameData.put(\commandStatus, bufferStream.next);
				frameData.put('commandData', getRemainingBytes.value(bufferStream));
				parseSuccess = true;
			}
		);
		if(parseSuccess,
			{
				device.prGotAPIFrame(frameType, frameData);
			},
			{
				"Unsucessful parsing of frame: %".format(frameByteBuffer).postln;
			}
		)
	}
}
