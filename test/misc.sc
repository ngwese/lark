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


(

var notes, on, off;

MIDIClient.init;
MIDIIn.connectAll;

~engine = Falco.new(s);

notes = Array.newClear(128);

MIDIdef.noteOn(\noteOn, { arg vel, num, chan, src;
  notes[num] = ~engine.noteOn(
    hz: num.midicps,
    amp: vel * 0.00315,
    atk: 1,
    decay: 0.4,
    sus: 0.85,
    rel: 4,
  );
});

MIDIdef.noteOff(\noteOff, { arg vel, num, chan, src;
  notes[num].set(\gate, 0);
  notes[num].release;

});

)

~engine.buffers[26]
~engine.oscA_type = \falco_silent;

(
~engine.oscA_table = FalcoTable.fromFile(
  s,
  "/Library/Audio/Presets/Xfer Records/Serum Presets/Tables/Analog/SawRounded.wav",
  2048
);
)

~engine.oscA_table = ~engine.default_table

~engine.default_table.buffers
~engine.oscA_table.buffers

