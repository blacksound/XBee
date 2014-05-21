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

	*new{arg parent, addressHi, addressLo, networkAddress, nodeIdentifier;
		^super.newCopyArgs(parent, addressHi, addressLo, networkAddress, nodeIdentifier).init;
	}

	init{
		//ensure that node identifier is symbol
		nodeIdentifier !? {nodeIdentifier = nodeIdentifier.asSymbol;};
	}

	route_{arg newRoute;
		parent.childDeviceRoutes.put(addressLo, newRoute);
	}

	route{
		^parent.childDeviceRoutes.at(addressLo);
	}

	sendTXData{arg bytes, sendFrameID;
		parent.sendTransmitRequest(addressHi, addressLo, networkAddress, bytes, sendFrameID);
	}
}

XBeeCoordinatorProxy : XBeeDeviceProxy {

}

XBeeRouterProxy : XBeeDeviceProxy {

}

XBeeEndDeviceProxy : XBeeDeviceProxy {

}

