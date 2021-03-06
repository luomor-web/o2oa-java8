package com.x.processplatform.service.processing.jaxrs.task;

import java.util.Date;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import javax.script.CompiledScript;
import javax.script.ScriptContext;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

import com.google.gson.JsonElement;
import com.x.base.core.container.EntityManagerContainer;
import com.x.base.core.container.factory.EntityManagerContainerFactory;
import com.x.base.core.entity.annotation.CheckPersistType;
import com.x.base.core.entity.annotation.CheckRemoveType;
import com.x.base.core.project.annotation.ActionLogger;
import com.x.base.core.project.config.Config;
import com.x.base.core.project.exception.ExceptionEntityNotExist;
import com.x.base.core.project.executor.ProcessPlatformExecutorFactory;
import com.x.base.core.project.http.ActionResult;
import com.x.base.core.project.http.EffectivePerson;
import com.x.base.core.project.jaxrs.WoId;
import com.x.base.core.project.logger.Logger;
import com.x.base.core.project.logger.LoggerFactory;
import com.x.base.core.project.script.ScriptFactory;
import com.x.base.core.project.tools.ListTools;
import com.x.processplatform.core.entity.content.Task;
import com.x.processplatform.core.entity.content.TaskCompleted;
import com.x.processplatform.core.entity.content.Work;
import com.x.processplatform.core.entity.element.ActivityType;
import com.x.processplatform.core.entity.element.Manual;
import com.x.processplatform.core.entity.element.Process;
import com.x.processplatform.core.express.ProcessingAttributes;
import com.x.processplatform.core.express.service.processing.jaxrs.task.WrapProcessing;
import com.x.processplatform.service.processing.Business;
import com.x.processplatform.service.processing.MessageFactory;
import com.x.processplatform.service.processing.WorkContext;
import com.x.processplatform.service.processing.WorkDataHelper;
import com.x.processplatform.service.processing.configurator.ProcessingConfigurator;
import com.x.processplatform.service.processing.processor.AeiObjects;

class ActionProcessing extends BaseAction {

	@ActionLogger
	private static Logger logger = LoggerFactory.getLogger(ActionProcessing.class);

	ActionResult<Wo> execute(EffectivePerson effectivePerson, String id, JsonElement jsonElement) throws Exception {

		final Wi wi = this.convertToWrapIn(jsonElement, Wi.class);

		String job;

		try (EntityManagerContainer emc = EntityManagerContainerFactory.instance().create()) {
			Task task = emc.fetch(id, Task.class, ListTools.toList(Task.job_FIELDNAME));
			if (null == task) {
				throw new ExceptionEntityNotExist(id, Task.class);
			}
			job = task.getJob();
		}

		return ProcessPlatformExecutorFactory.get(job).submit(new CallableExecute(wi, id)).get(300, TimeUnit.SECONDS);

	}

	private class CallableExecute implements Callable<ActionResult<Wo>> {

		private Wi wi;

		private String id;

		private CallableExecute(Wi wi, String id) {
			this.wi = wi;
			this.id = id;
		}

		private void callManualBeforeTaskScript(Business business, Task task) throws Exception {
			if (Objects.equals(task.getActivityType(), ActivityType.manual)) {
				Manual manual = business.element().get(task.getActivity(), Manual.class);
				if ((null != manual) && (StringUtils.isNotEmpty(manual.getManualBeforeTaskScript())
						|| StringUtils.isNotEmpty(manual.getManualBeforeTaskScriptText()))) {
					Work work = business.entityManagerContainer().find(task.getWork(), Work.class);
					if (null != work) {
						AeiObjects aeiObjects = new AeiObjects(business, work, manual, new ProcessingConfigurator(),
								new ProcessingAttributes());
						ScriptContext scriptContext = aeiObjects.scriptContext();
						((WorkContext) scriptContext.getAttribute(ScriptFactory.BINDING_NAME_WORKCONTEXT))
								.bindTask(task);
						WorkDataHelper workDataHelper = new WorkDataHelper(business.entityManagerContainer(), work);
						CompiledScript cs = null;
						cs = business.element().getCompiledScript(task.getApplication(), manual,
								Business.EVENT_MANUALBEFORETASK);
						cs.eval(scriptContext);
						workDataHelper.update(aeiObjects.getData());
						business.entityManagerContainer().commit();
					}
				}
			}
		}

		private void callManualAfterTaskScript(Business business, Task task, TaskCompleted taskCompleted)
				throws Exception {
			if (Objects.equals(task.getActivityType(), ActivityType.manual)) {
				Manual manual = business.element().get(task.getActivity(), Manual.class);
				if ((null != manual) && (StringUtils.isNotEmpty(manual.getManualAfterTaskScript())
						|| StringUtils.isNotEmpty(manual.getManualAfterTaskScriptText()))) {
					Work work = business.entityManagerContainer().find(task.getWork(), Work.class);
					if (null != work) {
						AeiObjects aeiObjects = new AeiObjects(business, work, manual, new ProcessingConfigurator(),
								new ProcessingAttributes());
						ScriptContext scriptContext = aeiObjects.scriptContext();
						((WorkContext) scriptContext.getAttribute(ScriptFactory.BINDING_NAME_WORKCONTEXT))
								.bindTaskCompleted(taskCompleted);
						CompiledScript cs = null;
						cs = business.element().getCompiledScript(task.getApplication(), manual,
								Business.EVENT_MANUALAFTERTASK);
						cs.eval(scriptContext);
					}
				}
			}
		}

		public ActionResult<Wo> call() throws Exception {
			ActionResult<Wo> result = new ActionResult<>();
			Wo wo = new Wo();
			try (EntityManagerContainer emc = EntityManagerContainerFactory.instance().create()) {
				Business business = new Business(emc);
				// ???????????????Wi,?????????????????????processType
				if (null == wi.getProcessingType()) {
					wi.setProcessingType(TaskCompleted.PROCESSINGTYPE_TASK);
				}
				Task task = emc.find(id, Task.class);
				if (null == task) {
					throw new ExceptionEntityNotExist(id, Task.class);
				}
				// ??????????????????
				callManualBeforeTaskScript(business, task);
				// ?????????????????????
				emc.beginTransaction(TaskCompleted.class);
				emc.beginTransaction(Task.class);
				// ????????????????????????lastest??????false
				emc.listEqualAndEqual(TaskCompleted.class, TaskCompleted.job_FIELDNAME, task.getJob(),
						TaskCompleted.person_FIELDNAME, task.getPerson()).forEach(o -> {
							o.setLatest(false);
						});
				Date now = new Date();
				Long duration = Config.workTime().betweenMinutes(task.getStartTime(), now);
				TaskCompleted taskCompleted = new TaskCompleted(task, wi.getProcessingType(), now, duration);
				if (StringUtils.isEmpty(taskCompleted.getOpinion())) {
					Process process = business.element().get(task.getProcess(), Process.class);
					if ((null != process) && BooleanUtils.isTrue(process.getRouteNameAsOpinion())) {
						// ?????????????????????
						taskCompleted.setOpinion(StringUtils.trimToEmpty(ListTools.parallel(task.getRouteNameList(),
								task.getRouteName(), task.getRouteOpinionList())));
						// ??????????????????????????????????????????????????????????????????????????????
						if (StringUtils.isEmpty(taskCompleted.getOpinion())) {
							taskCompleted.setOpinion(task.getRouteName());
						}
					}
				}
				taskCompleted.onPersist();
				emc.persist(taskCompleted, CheckPersistType.all);
				emc.remove(task, CheckRemoveType.all);
				emc.commit();
				/* ?????????????????????,??????????????????. */
				callManualAfterTaskScript(business, task, taskCompleted);
				MessageFactory.task_to_taskCompleted(taskCompleted);
				wo.setId(taskCompleted.getId());
			}
			result.setData(wo);
			return result;
		}
	}

	public static class Wo extends WoId {
	}

	public static class Wi extends WrapProcessing {

	}

}
