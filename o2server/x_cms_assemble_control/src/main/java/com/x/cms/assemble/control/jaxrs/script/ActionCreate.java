package com.x.cms.assemble.control.jaxrs.script;

import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import com.google.gson.JsonElement;
import com.x.base.core.container.EntityManagerContainer;
import com.x.base.core.container.factory.EntityManagerContainerFactory;
import com.x.base.core.entity.annotation.CheckPersistType;
import com.x.base.core.project.annotation.FieldDescribe;
import com.x.base.core.project.cache.ApplicationCache;
import com.x.base.core.project.cache.CacheManager;
import com.x.base.core.project.gson.GsonPropertyObject;
import com.x.base.core.project.http.ActionResult;
import com.x.base.core.project.http.EffectivePerson;
import com.x.base.core.project.jaxrs.WoId;
import com.x.cms.assemble.control.ExceptionWrapInConvert;
import com.x.cms.core.entity.AppInfo;
import com.x.cms.core.entity.Log;
import com.x.cms.core.entity.element.Script;

class ActionCreate extends BaseAction {

	ActionResult<Wo> execute( HttpServletRequest request, EffectivePerson effectivePerson, JsonElement jsonElement )
			throws Exception {
		ActionResult<Wo> result = new ActionResult<>();
		Wi wrapIn = null;
		Boolean check = true;
		
		try {
			wrapIn = this.convertToWrapIn( jsonElement, Wi.class );
		} catch (Exception e ) {
			check = false;
			Exception exception = new ExceptionWrapInConvert( e, jsonElement );
			result.error( exception );
			e.printStackTrace();
		}
		
		if ( check && wrapIn != null) {
			try (EntityManagerContainer emc = EntityManagerContainerFactory.instance().create()) {
				AppInfo appInfo = emc.find(wrapIn.getAppId(), AppInfo.class);
				if (null == appInfo) {
					throw new Exception("[post]appinfo{id:" + wrapIn.getAppId() + "} not existed.");
				}
				emc.beginTransaction(Script.class);
				Script script = new Script();
				wrapIn.copyTo( script );
				script.setCreatorPerson( effectivePerson.getDistinguishedName());
				script.setLastUpdatePerson( effectivePerson.getDistinguishedName());
				script.setLastUpdateTime(new Date());
				emc.persist(script, CheckPersistType.all);
				emc.commit();
				// ???????????????Script??????
				CacheManager.notify(Script.class);

				// ????????????
				emc.beginTransaction(Log.class);
				logService.log(emc, effectivePerson.getDistinguishedName(), script.getName(), script.getAppId(), "", "", script.getId(), "SCRIPT", "??????");
				emc.commit();
				
				Wo wo = new Wo();
				wo.setId( script.getId() );
				result.setData(wo);
			}
		}
		return result;
	}
	
	public class Wi extends GsonPropertyObject {

		@FieldDescribe("????????????.")
		private Date createTime;
		
		@FieldDescribe("????????????.")
		private Date updateTime;
		
		@FieldDescribe("ID.")
		private String id;
		
		@FieldDescribe("????????????.")
		private String name;
		
		@FieldDescribe("????????????.")
		private String alias;
		
		@FieldDescribe("????????????.")
		private String description;
		
		@FieldDescribe("??????????????????.")
		private Boolean validated;
		
		@FieldDescribe("????????????ID.")
		private String appId;
		
		@FieldDescribe("????????????.")
		private String text;
		
		@FieldDescribe("???????????????ID??????.")
		private List<String> dependScriptList;

		public Date getCreateTime() {
			return createTime;
		}

		public void setCreateTime(Date createTime) {
			this.createTime = createTime;
		}

		public Date getUpdateTime() {
			return updateTime;
		}

		public void setUpdateTime(Date updateTime) {
			this.updateTime = updateTime;
		}

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getAlias() {
			return alias;
		}

		public void setAlias(String alias) {
			this.alias = alias;
		}

		public String getDescription() {
			return description;
		}

		public void setDescription(String description) {
			this.description = description;
		}

		public Boolean getValidated() {
			return validated;
		}

		public void setValidated(Boolean validated) {
			this.validated = validated;
		}

		public String getAppId() {
			return appId;
		}

		public void setAppId(String appId) {
			this.appId = appId;
		}

		public String getText() {
			return text;
		}

		public void setText(String text) {
			this.text = text;
		}

		public List<String> getDependScriptList() {
			return dependScriptList;
		}

		public void setDependScriptList(List<String> dependScriptList) {
			this.dependScriptList = dependScriptList;
		}

	}
	
	public static class Wo extends WoId {

	}
}
