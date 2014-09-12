package com.violet.action;

import jodd.log.Logger;
import jodd.log.LoggerFactory;
import jodd.madvoc.meta.Action;
import jodd.madvoc.meta.MadvocAction;

@MadvocAction
public class IndexAction {

	private static Logger log = LoggerFactory.getLogger(IndexAction.class);

	@Action("/")
	public String view() {
		log.info("success");
		return "index";

	}
}
