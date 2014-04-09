package com.github.rmannibucau.rules.api;

public class LifecycleUnitException extends RuntimeException {
	public LifecycleUnitException(final Exception e) {
		super(e);
	}

	public LifecycleUnitException(final String s) {
		super(s);
	}
}
