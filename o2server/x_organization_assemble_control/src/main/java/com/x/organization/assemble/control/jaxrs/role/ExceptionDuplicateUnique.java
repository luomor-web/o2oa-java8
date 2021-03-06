package com.x.organization.assemble.control.jaxrs.role;

import com.x.base.core.project.exception.LanguagePromptException;

class ExceptionDuplicateUnique extends LanguagePromptException {

	private static final long serialVersionUID = 4132300948670472899L;

	ExceptionDuplicateUnique(String name, String unique) {
		super("角色 {} 的唯一标识:{},不能和已有的标识冲突.", name, unique);
	}
}
