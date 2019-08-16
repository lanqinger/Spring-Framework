/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.servlet.view;

import java.util.Locale;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.xml.ResourceEntityResolver;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.io.Resource;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.View;

/**
 * 一个{@link org.springframework.web.servlet.ViewResolver}实现，
 * 它使用专用XML文件中的bean定义来查看由资源位置指定的视图定义。
 * 该文件通常位于WEB-INF目录中;默认为“/WEB-INF/views.xml”。
 *
 * <p>此{@code ViewResolver}不支持其定义资源级别的国际化。
 * 如果您考虑{@link ResourceBundleViewResolver}
 * 需要为每个区域设置应用不同的视图资源。
 *
 * <p>Note: This {@code ViewResolver} implements the {@link Ordered} interface
 * in order to allow for flexible participation in {@code ViewResolver} chaining.
 * For example, some special views could be defined via this {@code ViewResolver}
 * (giving it 0 as "order" value), while all remaining views could be resolved by
 * a {@link UrlBasedViewResolver}.
 *
 * @author Juergen Hoeller
 * @since 18.06.2003
 * @see org.springframework.context.ApplicationContext#getResource
 * @see ResourceBundleViewResolver
 * @see UrlBasedViewResolver
 */
public class XmlViewResolver extends AbstractCachingViewResolver
		implements Ordered, InitializingBean, DisposableBean {

	/** Default if no other location is supplied */
	public static final String DEFAULT_LOCATION = "/WEB-INF/views.xml";


	private Resource location;

	private ConfigurableApplicationContext cachedFactory;

	private int order = Ordered.LOWEST_PRECEDENCE;  // default: same as non-Ordered


	/**
	 * Set the location of the XML file that defines the view beans.
	 * <p>The default is "/WEB-INF/views.xml".
	 * @param location the location of the XML file.
	 */
	public void setLocation(Resource location) {
		this.location = location;
	}

	/**
	 * Specify the order value for this ViewResolver bean.
	 * <p>The default value is {@code Ordered.LOWEST_PRECEDENCE}, meaning non-ordered.
	 * @see org.springframework.core.Ordered#getOrder()
	 */
	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	/**
	 * Pre-initialize the factory from the XML file.
	 * Only effective if caching is enabled.
	 */
	@Override
	public void afterPropertiesSet() throws BeansException {
		if (isCache()) {
			initFactory();
		}
	}


	/**
	 * This implementation returns just the view name,
	 * as XmlViewResolver doesn't support localized resolution.
	 */
	@Override
	protected Object getCacheKey(String viewName, Locale locale) {
		return viewName;
	}

	@Override
	protected View loadView(String viewName, Locale locale) throws BeansException {
		BeanFactory factory = initFactory();
		try {
			return factory.getBean(viewName, View.class);
		}
		catch (NoSuchBeanDefinitionException ex) {
			// Allow for ViewResolver chaining...
			return null;
		}
	}

	/**
	 * Initialize the view bean factory from the XML file.
	 * Synchronized because of access by parallel threads.
	 * @throws BeansException in case of initialization errors
	 */
	protected synchronized BeanFactory initFactory() throws BeansException {
		if (this.cachedFactory != null) {
			return this.cachedFactory;
		}

		Resource actualLocation = this.location;
		if (actualLocation == null) {
			actualLocation = getApplicationContext().getResource(DEFAULT_LOCATION);
		}

		// Create child ApplicationContext for views.
		GenericWebApplicationContext factory = new GenericWebApplicationContext();
		factory.setParent(getApplicationContext());
		factory.setServletContext(getServletContext());

		// Load XML resource with context-aware entity resolver.
		XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(factory);
		reader.setEnvironment(getApplicationContext().getEnvironment());
		reader.setEntityResolver(new ResourceEntityResolver(getApplicationContext()));
		reader.loadBeanDefinitions(actualLocation);

		factory.refresh();

		if (isCache()) {
			this.cachedFactory = factory;
		}
		return factory;
	}


	/**
	 * Close the view bean factory on context shutdown.
	 */
	@Override
	public void destroy() throws BeansException {
		if (this.cachedFactory != null) {
			this.cachedFactory.close();
		}
	}

}
