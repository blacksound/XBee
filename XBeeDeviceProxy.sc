//A XBeeDeviceProxy is a XBee device connected via another XBee device's network.
//It makes no difference for the device proxy represtation whether the remote device
//is in AT or API mode.
//This class is a form a 'friend' class of the parent
XBeeDeviceProxy {
	var <parent;
	var <addressHi;
	var <addressLo;
	var <networkAddress;
	var <nodeIdentifier;
	var <panID;
	var <>rxAction;
	var <queueUpdateInterval = 0.02;
	var txQueue, txQueueRoutine;
	var <rssiMonitorRoutine;
	var <signalStrength;
	var <online = true; //assuming online status at object creation time
	var <rssi;

	*new{arg parent, addressHi, addressLo, networkAddress, nodeIdentifier;
		^super.newCopyArgs(parent, addressHi, addressLo, networkAddress, nodeIdentifier).init;
	}

	init{
		//ensure that node identifier is symbol
		nodeIdentifier !? {nodeIdentifier = nodeIdentifier.asSymbol;};
		txQueue = LinkedList.new;
		txQueueRoutine = Routine({
			var msg;
			loop{
				msg = txQueue.popFirst;
				if(msg.notNil, {
					"Sending from queue: %".format(msg).postln;
					this.sendTXData(*msg);
				}, {/*"Stopping and reset".postln;*/thisThread.stop;});
				queueUpdateInterval.wait;
			};
		});

		//monitoring the connection by default
		rssiMonitorRoutine = fork{
			var cond, rssiResponseFunc;
			cond = Condition.new;
			rssiResponseFunc = {arg data;
				rssi = data[\commandData];
				cond.test = data[\commandStatus] != 4;
				cond.signal;
			};
			SimpleController.new(this).put(\online, {arg theChanged, what, more;
				cond.test = theChanged.online;
				cond.signal;
			}.inEnvir);
			loop{
				this.sendATCommand(\ReceivedSignalStrength, responseAction: rssiResponseFunc.inEnvir);
				cond.wait;
				cond.test = false;
				2.0.wait;
			};
		};

	}


	route_{arg newRoute;
		parent.childDeviceRoutes.put(addressLo, newRoute);
	}

	route{
		^parent.childDeviceRoutes.at(addressLo);
	}

	sendTXData{arg bytes, sendFrameID = false;
		online.if{parent.sendTransmitRequest(addressHi, addressLo, networkAddress, bytes, sendFrameID); /*"sentTX".postln*/};
	}

	sendTXDataQueued{arg bytes, sendFrameID;
		txQueue.add([bytes, sendFrameID]);
		if(txQueueRoutine.isPlaying.not, {
			/*"Starting queue playing".postln;*/
			txQueueRoutine.reset;
			txQueueRoutine.play;
		});
	}

	sendATCommand{arg cmd, parameterBytes, responseAction;
		online.if{parent.sendRemoteATCommandRequest(addressHi, addressLo, networkAddress, cmd, parameterBytes, responseAction); /*"sentAT".postln*/};
	}

	queueUpdateInterval_{arg val;
		queueUpdateInterval = val.clip(0.001, 1);
	}

	addressHi_ {arg val; addressHi = val; this.changed(\addressHi);}
	addressLo_ {arg val; addressLo = val; this.changed(\addressLo);}
	networkAddress_ {arg val; networkAddress = val; this.changed(\networkAddress);}
	nodeIdentifier_ {arg val; nodeIdentifier = val; this.changed(\nodeIdentifier);}

	prGotCommandResponse{arg frameData;
		//intercept command status to monitor online status
		frameData[\commandStatus] !? {this.prRemoteTransmissionStatus(frameData[\commandStatus] != 4);};

		//		"Command response: % %".format(nodeIdentifier, frameData).postln;
	}

	prRemoteTransmissionStatus{arg val;
		if(val,
			{
				if(online.not, {
					online = true;
					"XBee is online: %".format(nodeIdentifier).postln;
					this.changed(\online, online);
				});
			},
			{
				if(online, {
					online = false;
					"XBee is offline: %".format(nodeIdentifier).postln;
					this.changed(\online, online);
				});
			}
		);
	}
}

XBeeCoordinatorProxy : XBeeDeviceProxy {

}

XBeeRouterProxy : XBeeDeviceProxy {

}

XBeeEndDeviceProxy : XBeeDeviceProxy {

}

