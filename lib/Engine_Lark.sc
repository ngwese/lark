Engine_Lark : CroneEngine {
  var <lark;
  var <voices;

  *new { arg context, doneCallback;
		^super.new(context, doneCallback);
	}

  alloc {
    lark = Lark.new(this.context.server, this.context.xg, this.context.out_b);

    context.server.sync;

    this.addCommand(\start, "iff", { arg msg;
      var n = msg[1];

      // if re-using id of an active voice, kill the voice
      // TODO: should this fade the voice out?
      if (lark.voiceStarted[n], {
        Post << "Stopping voice [" << n << "]\n";
        lark.stop(n);
      });

      Post << "voice " << n << " start(" << msg[2] << ", " << msg[3] << ")\n";

      lark.start(n, msg[2], msg[3]);
    });

    this.addCommand(\stop, "i", { arg msg;
      lark.stop(msg[1]);
    });

    this.addCommand(\control, "sf", { arg msg;
      // MAINT: temporary stop gap until commands are defined for all messages
      var control = msg[1].asSymbol;
      lark.setControl(control, msg[2]);
    });

    lark.globalControls.keysValuesDo({ arg key, value;
      this.addCommand(key, "f", { arg msg;
        lark.globalControls.at(key).set(msg[1]);
      });
    });

    this.addCommand(\voice_pitch, "if", { arg msg;
      var v = lark.voices[msg[1]];
      if (v.notNil) {
        v.setPitch(msg[2]);
      };
    });

    this.addCommand(\voice_amp, "if", { arg msg;
      var v = lark.voices[msg[1]];
      if (v.notNil) {
        v.setAmp(msg[2]);
      };
    });

    this.addCommand(\osc1_table, "is", { arg msg;
      var v = lark.voices[msg[1]];
      var t;
      if (v.notNil) {
        t = LarkRegistry.at(msg[2]);
        if(t.notNil, {
          v.osc1 = lark.oscSpec(t, lark.osc1_mappings);
        }, {
          Post << "Lark: unknown table: '" << msg[2] << "'\n";
        });
      }
    });
  }

  free {
    lark.free;
  }
}