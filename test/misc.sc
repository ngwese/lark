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

~w = LarkWaves.fromFile("/Applications/WaveEdit/banks/ROM B.wav", 256);

~w.size

~d = File.readAllString("~/wt.json".standardizePath);
~d.parseJSON;

~z = "~/wt.json".standardizePath.parseJSONFile;
~z["tables"].size


(

var notes, on, off;

MIDIClient.init;
MIDIIn.connectAll;

~engine = Lark.new(s);
~engine.oscA_type = \lark_osc3;
~engine.oscA_table = LarkTable.fromFile(
  s,
  "/Library/Audio/Presets/Xfer Records/Serum Presets/Tables/Analog/SawRounded.wav",
  2048
);


~notes = Array.newClear(128);

MIDIdef.noteOn(\noteOn, { arg vel, num, chan, src;
  ~notes[num] = ~engine.noteOn2(
    hz: num.midicps,
    amp: vel * 0.00315,
    // atk: 1,
    // decay: 0.4,
    // sus: 0.85,
    // rel: 4,
  );
});

MIDIdef.noteOff(\noteOff, { arg vel, num, chan, src;
  // notes[num].set(\gate, 0);
  ~notes[num].stop;
  ~notes[num].release;

});

)

~engine.oscA_table


~engine.osc1Spec
~engine.posSpec

~engine.server
~v = LarkVoice.new
~x = ~v.start(~engine.server, ~engine.voicesGroup, 0, ~engine.osc1Spec, ~engine.ampSpec, [~engine.posSpec]);
~x.modBusses
~x.modSources

~x.gateBus.get
~x.pitchBus.get
~x.ampBus.get
~x.stop
~x.gateBus.set(1);
~x.pitchBus.set(80);
~x.ampBus.set(0.1);

~engine.buffers[26]
~engine.oscA_type = \lark_silent;

(
~engine.oscA_table = LarkTable.fromFile(
  s,
  "/Library/Audio/Presets/Xfer Records/Serum Presets/Tables/Analog/SawRounded.wav",
  2048
);
)

~engine.oscA_table = ~engine.default_table

~engine.default_table.buffers
~engine.oscA_table.buffers

~engine.mod_group
~engine.params


x = [\one, \two, \three]
y = Array.fill(x.size, { Bus.control(s, 1) })
x.collectAs({|item, i| item -> y[i]}, Dictionary)

(
[\a, 1] ++ [\b, 2, \c, 5].postln;
)