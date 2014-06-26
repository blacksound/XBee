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
	}

	route_{arg newRoute;
		parent.childDeviceRoutes.put(addressLo, newRoute);
	}

	route{
		^parent.childDeviceRoutes.at(addressLo);
	}

	sendTXData{arg bytes, sendFrameID = false;
		parent.sendTransmitRequest(addressHi, addressLo, networkAddress, bytes, sendFrameID);
	}

	sendTXDataQueued{arg bytes, sendFrameID;
		txQueue.add([bytes, sendFrameID]);
		if(txQueueRoutine.isPlaying.not, {
			/*"Starting queue playing".postln;*/
			txQueueRoutine.reset;
			txQueueRoutine.play;
		});
	}

	queueUpdateInterval_{arg val;
		queueUpdateInterval = val.clip(0.001, 1);
	}

	addressHi_ {arg val; addressHi = val; this.changed(\addressHi);}
	addressLo_ {arg val; addressLo = val; this.changed(\addressLo);}
	networkAddress_ {arg val; networkAddress = val; this.changed(\networkAddress);}
	nodeIdentifier_ {arg val; nodeIdentifier = val; this.changed(\nodeIdentifier);}
}

XBeeCoordinatorProxy : XBeeDeviceProxy {

}

XBeeRouterProxy : XBeeDeviceProxy {

}

XBeeEndDeviceProxy : XBeeDeviceProxy {

}

