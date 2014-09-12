package com.violet.utils;

import jodd.madvoc.WebApplication;
import jodd.madvoc.component.MadvocConfig;
import jodd.madvoc.petite.PetiteFilterManager;
import jodd.madvoc.petite.PetiteInterceptorManager;
import jodd.madvoc.petite.PetiteMadvocController;
import jodd.madvoc.petite.PetiteResultsManager;

public abstract class DefaultWebApplication extends WebApplication {
	protected final DefaultAppCore defaultAppCore;

	protected DefaultWebApplication() {
		defaultAppCore = createAppCore();
	}

	protected abstract DefaultAppCore createAppCore();

	@Override
	protected void initWebApplication() {
		defaultAppCore.start();
		super.initWebApplication();
	}

	@Override
	public final void registerMadvocComponents() {
		super.registerMadvocComponents();

		registerComponent("petiteContainer", defaultAppCore.getPetite());

		registerCustomMadvocComponents();

		registerComponent(PetiteMadvocController.class);
		registerComponent(PetiteFilterManager.class);
		registerComponent(PetiteInterceptorManager.class);
		registerComponent(PetiteResultsManager.class);
	}

	protected void registerCustomMadvocComponents() {

	}

	@Override
	protected void destroy(MadvocConfig madvocConfig) {
		defaultAppCore.stop();
		super.destroy(madvocConfig);
	}
}
