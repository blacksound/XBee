SerialPort.devices;
(
~coordinator = XBeeDeviceAPIMode(SerialPort('/dev/tty.usbserial-A600ezu2', 9600, crtscts: true));
~coordinator.start;
~coordinator.rxAction_({arg byte; "Coordinator received: \t%\t%\t%".format(byte.asHexString(2), byte, byte.asAscii).postln;});
~coordinatorData = (address: [0x00, 0x13, 0xA2, 0x00, 0x40, 0x90, 0x28, 0x75], networkAddress: [0x00, 0x00]);
~coordinator.responseActions.put(\ZigBeeReceivePacket, {arg realAddr, networkAddr, data;
	"ZigBee RX: %".format([realAddr, networkAddr, data]).postln;
});

~router = XBeeDeviceAPIMode(SerialPort('/dev/tty.usbserial-FTCVPEA5', 9600, crtscts:true));
~router.start;
~router.rxAction_({arg byte; "Router received: \t%\t%\t%".format(byte.asHexString(2), byte, byte.asAscii).postln;});
~routerData = (address: [0x00, 0x13, 0xA2, 0x00, 0x40, 0x90, 0x29, 0x1B], networkAddress: [0xFF, 0xFE]);
)
//~ledData = (address: [0x00, 0x13, 0xA2, 0x00, 0x40, 0x90, 0x29, 0x1B], networkAddress: [0xFF, 0xFE]);
//)
~coordinator.sendRew;lmermoteTX(~routerData.address, ~routerData.networkAddress, "aaaaa".ascii, 1);
~coordinator.sendRemoteTX(~ledData.address, ~ledData.networkAddress, ["ms".ascii, 100, 13, 10].flatten, 1);
~coordinator.sendRemoteTX(~ledData.address, ~ledData.networkAddress, ["ms".ascii, 0, 13, 10].flatten, 1);
//sending to led don't work
~router.sendRemoteTX(~coordinatorData.address, ~coordinatorData.networkAddress, "bbbbb".ascii, 1);
~router.sendRemoteTX(~ledData.address, ~ledData.networkAddress, ["ms".ascii, 100, 13, 10].flatten, 0);
~router.sendRemoteTX(~ledData.address, ~ledData.networkAddress, ["ms".ascii, 0, 13, 10].flatten, 0);
//sending to coordinator
~router.sendRemoteTX(~coordinatorData.address, ~coordinatorData.networkAddress, ["ms".ascii, 100, 13, 10].flatten, 0);
~router.sendRemoteTX(~coordinatorData.address, ~coordinatorData.networkAddress, ["ms".ascii, 0, 13, 10].flatten, 0);

//Check th MY address for led module
~router.sendRemoteATCommand(~ledData.address, ~ledData.networkAddress, "NI");
~coordinator.sendRemoteATCommand(~ledData.address, ~ledData.networkAddress, "NI");
~coordinator.sendRemoteATCommand(~routerData.address, ~routerData.networkAddress, "RE");
~coordinator.sendRemoteATCommand(~routerData.address, ~routerData.networkAddress, "NI");
~router.sendLocalATCommand("NI");
//check the baud rate for router
~coordinator.sendRemoteTX(~routerData.address, ~routerData.networkAddress, "hallo".ascii, 1);
//if router returns garbage there's a chance that the baud rates mismatch
//query the baudrate for the router, i.e. 19200
~coordinator.sendRemoteATCommand(~routerData.address, ~routerData.networkAddress, "D5", 1);
~coordinator.sendRemoteATCommand(~routerData.address, ~routerData.networkAddress, "WR");
~coordinator.sendRemoteATCommand(~routerData.address, ~routerData.networkAddress, "AC");
~coordinator.sendRemoteTX(~routerData.address, ~routerData.networkAddress, "hallo".ascii);
~coordinator.sendRemoteATCommand(~routerData.address, ~routerData.networkAddress, "BD", 3);
~router.serialPort.putAll("heisann");


(
~coordinator = XBeeCoordinatorAPIMode(SerialPort('/dev/tty.usbserial-A600ezu2', 9600, crtscts: true));
~coordinator.start;
~coordinator.rxAction_({arg byte; "Coordinator received: \t%\t%\t%".format(byte.asHexString(2), byte, byte.asAscii).postln;});
~router = SerialPort("/dev/tty.usbserial-FTCVPEA5", 9600, crtscts: true);
Tdef(\routerRead, {loop{"Router: %".format(~router.read).postln;}}).play;
)
~router.putAll("hiie");

~coordinator.getChildDeviceByName('TOG01').sendTXData("hei".ascii);
~coordinator.getChildDeviceByName('TOG01').sendTXData("hei".ascii, false);



~coordinator.sendATCommand('DestinationNode', "TOG01".ascii);

~coordinator.sendATCommand('DestinationAddressLow', responseAction:{arg frameData; "MYTURN:%".format(frameData).postln});
~coordinator.sendATCommand('DestinationAddressLow');

~coordinator.sendATCommand('NodeDiscover');
~coordinator.sendATCommand('NodeDiscover', "TOG01".ascii);

(
~coordinator.childDevices.keysValuesDo({arg key,val;
	"Name: %[%]\n\taddress: %\n\tserial number: %:%\n\troute:%".format(
		val.nodeIdentifier, val.class, val.networkAddress, val.addressHi, val.addressLo, val.route
	).postln;
})
)




a = Dictionary[234 -> {"hei".postln}]
b = a.removeAt(234)
a