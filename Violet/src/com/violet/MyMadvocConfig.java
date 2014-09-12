package com.violet;

import jodd.madvoc.component.MadvocConfig;
import jodd.madvoc.macro.RegExpPathMacros;

public class MyMadvocConfig extends MadvocConfig {
	public MyMadvocConfig() {
		super();

		getRootPackages().addRootPackageOf(MyWebApplication.class);
		setPathMacroClass(RegExpPathMacros.class);
		setResultPathPrefix("/WEB-INF/www/");
	}
}
