package funcatron.service.spring_boot;


import org.springframework.boot.context.embedded.AbstractEmbeddedServletContainerFactory;
import org.springframework.boot.context.embedded.EmbeddedServletContainer;
import org.springframework.boot.context.embedded.EmbeddedServletContainerException;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.mock.web.MockServletContext;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import java.util.*;

/**
 * A Mock server that's loaded as a component so requests can be sent to the component
 */
@Component
public class MockServer extends AbstractEmbeddedServletContainerFactory {

    private MockEmbeddedServletContainer container;

    @Override
    public EmbeddedServletContainer getEmbeddedServletContainer(
            ServletContextInitializer... initializers) {
        this.container = new MockEmbeddedServletContainer(
                mergeInitializers(initializers), getPort());
        return this.container;
    }

    public MockEmbeddedServletContainer getContainer() {
        return this.container;
    }

    public ServletContext getServletContext() {
        return getContainer() == null ? null : getContainer().servletContext;
    }

    public RegisteredServlet getRegisteredServlet(int index) {
        return getContainer() == null ? null
                : getContainer().getRegisteredServlets().get(index);
    }

    public RegisteredFilter getRegisteredFilter(int index) {
        return getContainer() == null ? null
                : getContainer().getRegisteredFilters().get(index);
    }

    public static class MockEmbeddedServletContainer implements EmbeddedServletContainer {

        private ServletContext servletContext;

        private final ServletContextInitializer[] initializers;

        private final List<RegisteredServlet> registeredServlets = new ArrayList<RegisteredServlet>();

        private final List<RegisteredFilter> registeredFilters = new ArrayList<RegisteredFilter>();

        private final int port;

        public MockEmbeddedServletContainer(ServletContextInitializer[] initializers, int port) {
            this.initializers = initializers;
            this.port = port;
            initialize();
        }

        private void initialize() {
            try {
                this.servletContext = new MockServletContext() {
                    @Override
                    public ServletRegistration.Dynamic addServlet(String servletName, String className) {
                        return null;
                    }

                    @Override
                    public ServletRegistration.Dynamic addServlet(String servletName, Servlet servlet) {
                        return null;
                    }

                    @Override
                    public ServletRegistration.Dynamic addServlet(String servletName, Class<? extends Servlet> servletClass) {
                        return null;
                    }


                    @Override
                    public FilterRegistration.Dynamic addFilter(String filterName, Filter filter) {
                        return null;
                    }

                };

                final Map<String, String> initParameters = new HashMap<String, String>();


                for (ServletContextInitializer initializer : this.initializers) {
                    initializer.onStartup(this.servletContext);
                }
            }
            catch (ServletException ex) {
                throw new RuntimeException(ex);
            }
        }

        @SuppressWarnings("unchecked")
        public static <T> Enumeration<T> emptyEnumeration() {
            return (Enumeration<T>) EmptyEnumeration.EMPTY_ENUMERATION;
        }

        @Override
        public void start() throws EmbeddedServletContainerException {
        }

        @Override
        public void stop() {
            this.servletContext = null;
            this.registeredServlets.clear();
        }

        public Servlet[] getServlets() {
            Servlet[] servlets = new Servlet[this.registeredServlets.size()];
            for (int i = 0; i < servlets.length; i++) {
                servlets[i] = this.registeredServlets.get(i).getServlet();
            }
            return servlets;
        }

        public List<RegisteredServlet> getRegisteredServlets() {
            return this.registeredServlets;
        }

        public List<RegisteredFilter> getRegisteredFilters() {
            return this.registeredFilters;
        }

        @Override
        public int getPort() {
            return this.port;
        }

        private static class EmptyEnumeration<E> implements Enumeration<E> {

            static final EmptyEnumeration<Object> EMPTY_ENUMERATION = new EmptyEnumeration<Object>();

            @Override
            public boolean hasMoreElements() {
                return false;
            }

            @Override
            public E nextElement() {
                throw new NoSuchElementException();
            }

        }

    }

    public static class RegisteredServlet {

        private final Servlet servlet;

        private final ServletRegistration.Dynamic registration;

        public RegisteredServlet(Servlet servlet) {
            this.servlet = servlet;
            this.registration =  new ServletRegistration.Dynamic() {
                @Override
                public void setLoadOnStartup(int loadOnStartup) {

                }

                @Override
                public void setMultipartConfig(MultipartConfigElement multipartConfig) {

                }

                @Override
                public void setRunAsRole(String roleName) {

                }

                @Override
                public Set<String> setServletSecurity(ServletSecurityElement constraint) {
                    return null;
                }

                /**
                 * Mark this Servlet/Filter as supported asynchronous processing.
                 *
                 * @param isAsyncSupported Should this Servlet/Filter support
                 *                         asynchronous processing
                 * @throws IllegalStateException if the ServletContext associated with
                 *                               this registration has already been initialised
                 */
                @Override
                public void setAsyncSupported(boolean isAsyncSupported) {

                }

                /**
                 * TODO
                 *
                 * @param urlPatterns The URL patterns that this Servlet should be mapped to
                 * @return TODO
                 * @throws IllegalArgumentException if urlPattern is null or empty
                 * @throws IllegalStateException    if the associated ServletContext has
                 *                                  already been initialised
                 */
                @Override
                public Set<String> addMapping(String... urlPatterns) {
                    return null;
                }

                @Override
                public Collection<String> getMappings() {
                    return null;
                }

                @Override
                public String getRunAsRole() {
                    return null;
                }

                @Override
                public String getName() {
                    return null;
                }

                @Override
                public String getClassName() {
                    return null;
                }

                /**
                 * Add an initialisation parameter if not already added.
                 *
                 * @param name  Name of initialisation parameter
                 * @param value Value of initialisation parameter
                 * @return <code>true</code> if the initialisation parameter was set,
                 * <code>false</code> if the initialisation parameter was not set
                 * because an initialisation parameter of the same name already
                 * existed
                 * @throws IllegalArgumentException if name or value is <code>null</code>
                 * @throws IllegalStateException    if the ServletContext associated with this
                 *                                  registration has already been initialised
                 */
                @Override
                public boolean setInitParameter(String name, String value) {
                    return false;
                }

                /**
                 * Get the value of an initialisation parameter.
                 *
                 * @param name The initialisation parameter whose value is required
                 * @return The value of the named initialisation parameter
                 */
                @Override
                public String getInitParameter(String name) {
                    return null;
                }

                /**
                 * Add multiple initialisation parameters. If any of the supplied
                 * initialisation parameter conflicts with an existing initialisation
                 * parameter, no updates will be performed.
                 *
                 * @param initParameters The initialisation parameters to add
                 * @return The set of initialisation parameter names that conflicted with
                 * existing initialisation parameter. If there are no conflicts,
                 * this Set will be empty.
                 * @throws IllegalArgumentException if any of the supplied initialisation
                 *                                  parameters have a null name or value
                 * @throws IllegalStateException    if the ServletContext associated with this
                 *                                  registration has already been initialised
                 */
                @Override
                public Set<String> setInitParameters(Map<String, String> initParameters) {
                    return null;
                }

                /**
                 * Get the names and values of all the initialisation parameters.
                 *
                 * @return A Map of initialisation parameter names and associated values
                 * keyed by name
                 */
                @Override
                public Map<String, String> getInitParameters() {
                    return null;
                }
            };
        }

        public ServletRegistration.Dynamic getRegistration() {
            return this.registration;
        }

        public Servlet getServlet() {
            return this.servlet;
        }

    }

    public static class RegisteredFilter {

        private final Filter filter;

        private final FilterRegistration.Dynamic registration;

        public RegisteredFilter(Filter filter) {
            this.filter = filter;
            this.registration = new FilterRegistration.Dynamic() {
                /**
                 * Add a mapping for this filter to one or more named Servlets.
                 *
                 * @param dispatcherTypes The dispatch types to which this filter should
                 *                        apply
                 * @param isMatchAfter    Should this filter be applied after any mappings
                 *                        defined in the deployment descriptor
                 *                        (<code>true</code>) or before?
                 * @param servletNames    Requests mapped to these servlets will be
                 *                        processed by this filter
                 * @throws IllegalArgumentException if the list of sevrlet names is empty
                 *                                  or null
                 * @throws IllegalStateException    if the associated ServletContext has
                 *                                  already been initialised
                 */
                @Override
                public void addMappingForServletNames(EnumSet<DispatcherType> dispatcherTypes, boolean isMatchAfter, String... servletNames) {

                }

                /**
                 * @return TODO
                 */
                @Override
                public Collection<String> getServletNameMappings() {
                    return null;
                }

                /**
                 * Add a mapping for this filter to one or more URL patterns.
                 *
                 * @param dispatcherTypes The dispatch types to which this filter should
                 *                        apply
                 * @param isMatchAfter    Should this filter be applied after any mappings
                 *                        defined in the deployment descriptor
                 *                        (<code>true</code>) or before?
                 * @param urlPatterns     The URL patterns to which this filter should be
                 *                        applied
                 * @throws IllegalArgumentException if the list of URL patterns is empty or
                 *                                  null
                 * @throws IllegalStateException    if the associated ServletContext has
                 *                                  already been initialised
                 */
                @Override
                public void addMappingForUrlPatterns(EnumSet<DispatcherType> dispatcherTypes, boolean isMatchAfter, String... urlPatterns) {

                }

                /**
                 * @return TODO
                 */
                @Override
                public Collection<String> getUrlPatternMappings() {
                    return null;
                }

                /**
                 * Mark this Servlet/Filter as supported asynchronous processing.
                 *
                 * @param isAsyncSupported Should this Servlet/Filter support
                 *                         asynchronous processing
                 * @throws IllegalStateException if the ServletContext associated with
                 *                               this registration has already been initialised
                 */
                @Override
                public void setAsyncSupported(boolean isAsyncSupported) {

                }

                @Override
                public String getName() {
                    return null;
                }

                @Override
                public String getClassName() {
                    return null;
                }

                /**
                 * Add an initialisation parameter if not already added.
                 *
                 * @param name  Name of initialisation parameter
                 * @param value Value of initialisation parameter
                 * @return <code>true</code> if the initialisation parameter was set,
                 * <code>false</code> if the initialisation parameter was not set
                 * because an initialisation parameter of the same name already
                 * existed
                 * @throws IllegalArgumentException if name or value is <code>null</code>
                 * @throws IllegalStateException    if the ServletContext associated with this
                 *                                  registration has already been initialised
                 */
                @Override
                public boolean setInitParameter(String name, String value) {
                    return false;
                }

                /**
                 * Get the value of an initialisation parameter.
                 *
                 * @param name The initialisation parameter whose value is required
                 * @return The value of the named initialisation parameter
                 */
                @Override
                public String getInitParameter(String name) {
                    return null;
                }

                /**
                 * Add multiple initialisation parameters. If any of the supplied
                 * initialisation parameter conflicts with an existing initialisation
                 * parameter, no updates will be performed.
                 *
                 * @param initParameters The initialisation parameters to add
                 * @return The set of initialisation parameter names that conflicted with
                 * existing initialisation parameter. If there are no conflicts,
                 * this Set will be empty.
                 * @throws IllegalArgumentException if any of the supplied initialisation
                 *                                  parameters have a null name or value
                 * @throws IllegalStateException    if the ServletContext associated with this
                 *                                  registration has already been initialised
                 */
                @Override
                public Set<String> setInitParameters(Map<String, String> initParameters) {
                    return null;
                }

                /**
                 * Get the names and values of all the initialisation parameters.
                 *
                 * @return A Map of initialisation parameter names and associated values
                 * keyed by name
                 */
                @Override
                public Map<String, String> getInitParameters() {
                    return null;
                }
            };
        }

        public FilterRegistration.Dynamic getRegistration() {
            return this.registration;
        }

        public Filter getFilter() {
            return this.filter;
        }

    }

}