//A XBeeDeviceProxy is a XBee device connected via another XBee device's network.
//It makes no difference for the device proxy represtation whether the remote device
//is in AT or API mode.

XBeeDeviceProxy {
	var <parent;
	var <addressHi;
	var <addressLo;
	var <networkAddress;
	var <nodeIdentifier;
	var <panID;

	*new{arg parent, addressHi, addressLo, networkAddress, nodeIdentifier;
		^super.newCopyArgs(parent, addressHi, addressLo, networkAddress, nodeIdentifier);
	}
}

XBeeCoordinatorProxy : XBeeDeviceProxy {

}

XBeeRouterProxy : XBeeDeviceProxy {

}

XBeeEndDeviceProxy : XBeeDeviceProxy {

}

