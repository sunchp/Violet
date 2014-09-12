package com.violet;

import com.violet.utils.DefaultAppCore;
import com.violet.utils.DefaultWebApplication;

public class MyWebApplication extends DefaultWebApplication {

	@Override
	protected DefaultAppCore createAppCore() {
		return new MyAppCore();
	}

	@Override
	protected void registerCustomMadvocComponents() {
		registerComponent(MyMadvocConfig.class);
	}
}
