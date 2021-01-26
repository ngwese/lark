Engine_Lark : CroneEngine {
  var <lark;
  var <voices;

  *new { arg context, doneCallback;
		^super.new(context, doneCallback);
	}

  alloc {
    lark = Lark.new(context.server, context.xg);
    voices = IdentityDictionary.new;

    context.server.sync;

    this.addCommand(\start, "iff", { arg msg;
      var id = msg[0];
      var existingVoice = voices[id];

      // if re-using id of an active voice, kil the voice
      // TODO: should this fade the voice out?
      if (existingVoice.notNil, {
        Post << "Killing voice [" << id << "]\n";
        existingVoice.free;
      });

      voices.add(id -> lark.noteOn(msg[1], msg[2]));
    });

    this.addCommand(\stop, "i", { arg msg;
      var id = msg[0];
      voices[id].stop;
      voices[id].release;
    });

    this.addCommand(\set_control, "sf", { arg msg;
      // MAINT: temporary stop gap until commands are defined for all messages
      var control = msg[0].asSymbol;
      lark.setControl(control, msg[1]);
    });
  }

  free {
    lark.free;
  }
}