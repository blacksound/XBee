SerialPort.devices;
(
~coordinator = XBeeDeviceAPIMode(SerialPort('/dev/tty.usbserial-A600ezu2', 9600, crtscts: true));
~coordinator.start;
~coordinator.rxAction_({arg byte; "Coordinator received: \t%\t%\t%".format(byte.asHexString(2), byte, byte.asAscii).postln;});
~coordinatorData = (address: [0x00, 0x13, 0xA2, 0x00, 0x40, 0x90, 0x28, 0x75], networkAddress: [0x00, 0x00]);

~router = XBeeDeviceAPIMode(SerialPort('/dev/tty.usbserial-FTCVPEA5', 9600, crtscts:true));
~router.start;
~router.rxAction_({arg byte; "Router received: \t%\t%\t%".format(byte.asHexString(2), byte, byte.asAscii).postln;});
~routerData = (address: [0x00, 0x13, 0xA2, 0x00, 0x40, 0x8C, 0xCA, 0xDC], networkAddress: [0xFF, 0xFE]);

~ledData = (address: [0x00, 0x13, 0xA2, 0x00, 0x40, 0x90, 0x29, 0x1B], networkAddress: [0xFF, 0xFE]);
)
~coordinator.sendRemoteTX(~routerData.address, ~routerData.networkAddress, "hallo".ascii, 1);
~coordinator.sendRemoteTX(~ledData.address, ~ledData.networkAddress, ["ms".ascii, 100, 13, 10].flatten, 1);
~coordinator.sendRemoteTX(~ledData.address, ~ledData.networkAddress, ["ms".ascii, 0, 13, 10].flatten, 1);
//sending to led don't work
~router.sendRemoteTX(~coordinatorData.address, ~coordinatorData.networkAddress, "hallo".ascii, 1);
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
~router.serialPort.put(23);
t = fork { loop { ~router.serialPort.putAll([11,22,33,44,55,66]); 0.1.wait} };
u = fork{loop{ ~coordinator.sendRemoteTX(~routerData.address, ~routerData.networkAddress, "hallo".ascii, 1);0.75.wait;}}

~router.sendLocalATCommand("D5", 0x01);
~router.sendLocalATCommand("1S");
~router.sendLocalATCommand("NI");
~coordinator.sendLocalATCommand("1S");

~router.sendLocalATCommand("%V");
0x6F / 1024

~coordinator.sendLocalATCommand("D5");
~router.serialPort.putAll("hei".ascii);


(
~togLED3 = (
	xbee: ~xbee,
	//	address: [0x00, 0x13, 0xA2, 0x00, 0x40, 0x90, 0x29, 0xEA],//TO
	address: [0x00, 0x13, 0xA2, 0x00, 0x40, 0x90, 0x29, 0x1B],//FEM
	networkAddress: [0xFF, 0xFE],
	theTask: Task.new({}),
	runUpdateTask: {arg ev;
		ev.use{
			if(~theTask.isPlaying.not, {
				~theTask = Task.new({
					loop {
						//~setLEDValue.value(~address, ~networkAddress, ~ledValue);
						if(~ledValue != ~lastTransmittedValue, {
							~xbee.sendRemoteTX(~address, ~networkAddress, ["ms".ascii, ~ledValue, 13, 10].flatten, 1);
							~lastTransmittedValue = ~ledValue;
						});
						0.05.wait;
					};
				}.inEnvir).play;
				SystemClock.sched(0.5, {
					if(~theTask.isPlaying, {
						~stopUpdateTask.value(currentEnvironment);
					});
				}.inEnvir);
			});
		}
	},
	stopUpdateTask: {arg ev;
		ev.use{
			~theTask.stop;
		}
	},
	ledValue: 0,
	lastTransmittedValue: 0,
	setLEDValue: {arg ev, val;
		ev[\ledValue] = val.clip(0, 250);
		ev[\runUpdateTask].value(ev);
	}
);
)

~togLED3.setLEDValue(100);
~togLED3.setLEDValue(0);

fork{ 255.do{|i| ~togLED3.setLEDValue(i); 0.01.wait;}}
fork{ (255..0).do{|i| ~togLED3.setLEDValue(i); 0.01.wait;}}

"NJ".ascii.collect(_.asHexString)
~xbee.sendLocalATCommand("MY");
~xbee.sendLocalATCommand("BD");
~xbee.sendRemoteTX(~togLED3.address, ~togLED3.networkAddress, ["ms".ascii, 100/*led val*/, 13, 10].flatten, 1);
~xbee.sendRemoteTX(~togLED3.address, ~togLED3.networkAddress, ["ms".ascii, 0/*led val*/, 13, 10].flatten, 1);
~xbee.sendRemoteATCommand(~togLED3.address, ~togLED3.networkAddress, "MY");

~firern = (address: [0x00, 0x13, 0xA2, 0x00, 0x40, 0x8C, 0xCA, 0xDC], networkAddress: [0xFF, 0xFE]);
~xbee.sendRemoteATCommand(~firern.address, ~firern.networkAddress, "MY");
~xbee.sendRemoteTX(~firern.address, ~firern.networkAddress, ["ms".ascii, 100/*led val*/, 13, 10].flatten, 1);
(

)

~xbee2.sendRemoteATCommand(~coordinator.address, ~coordinator.networkAddress, "MY");
~xbee2.sendRemoteTX(, ~togLED3.networkAddress, ["ms".ascii, 100/*led val*/, 13, 10].flatten, 1);

~coordinator.serialPort.put(44)


XBeeAPI.frameTypeByteCodes