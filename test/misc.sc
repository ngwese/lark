~p = "/Applications/WaveEdit/banks/ROM B.wav";
~b = Buffer.read(s, "/Applications/WaveEdit/banks/ROM B.wav");
~b.query;


~f = SoundFile.openRead(~p);
~x = Signal.newClear(~f.numFrames);
~f.readData(~x);
~x.size
mod(~x.size, 64)

~x[0..255].plot
~x.slice(0, 255).plot
~x.plot

~w = FalcoWaves.fromFile("/Applications/WaveEdit/banks/ROM B.wav", 256);

~w.size

~d = File.readAllString("~/wt.json".standardizePath);
~d.parseJSON;

~z = "~/wt.json".standardizePath.parseJSONFile;
~z["tables"].size

