/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2014 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2014 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.netmgt.poller.monitors;

import java.net.InetAddress;
import java.util.Map;

import javax.management.MalformedObjectNameException;

import org.jolokia.client.J4pClient;
import org.jolokia.client.J4pClientBuilder;
import org.jolokia.client.exception.J4pConnectException;
import org.jolokia.client.exception.J4pException;
import org.jolokia.client.exception.J4pRemoteException;
import org.jolokia.client.request.J4pExecRequest;
import org.jolokia.client.request.J4pExecResponse;
import org.jolokia.client.request.J4pReadRequest;
import org.jolokia.client.request.J4pReadResponse;
import org.opennms.core.utils.InetAddressUtils;
import org.opennms.core.utils.ParameterMap;
import org.opennms.core.utils.TimeoutTracker;
import org.opennms.netmgt.poller.Distributable;
import org.opennms.netmgt.poller.DistributionContext;
import org.opennms.netmgt.poller.MonitoredService;
import org.opennms.netmgt.poller.PollStatus;
import org.opennms.netmgt.poller.support.AbstractServiceMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is designed to be used by the service poller framework to test the
 * availability of a generic mbean method on remote interfaces via a jolokia
 * agent. The class implements the ServiceMonitor interface that allows it to be
 * used along with other plug-ins by the service poller framework.
 *
 * @author <A HREF="mailto:cliles@capario.com">Chris Liles</A>
 * @author <A HREF="http://www.opennms.org/">OpenNMS</a>
 */
@Distributable(DistributionContext.DAEMON)
final public class JolokiaBeanMonitor extends AbstractServiceMonitor {

    /**
     * Default port.
     */
    private static final int DEFAULT_PORT = 8080;

    /**
     * Default retries.
     */
    private static final int DEFAULT_RETRY = 0;

    /**
     * Default timeout. Specifies how long (in milliseconds) to block waiting
     * for data from the monitored interface.
     */
    private static final int DEFAULT_TIMEOUT = 3000; // 3 second timeout on
    // read()

    public static final String PARAMETER_URL = "url";
    public static final String PARAMETER_USERNAME = "auth-username";
    public static final String PARAMETER_PASSWORD = "auth-password";
    public static final String PARAMETER_BANNER = "banner";
    public static final String PARAMETER_PORT = "port";
    public static final String PARAMETER_BEANNAME = "beanname";
    public static final String PARAMETER_ATTRNAME = "attrname";
    public static final String PARAMETER_ATTRPATH = "attrpath";
    public static final String PARAMETER_METHODNAME = "methodname";
    public static final String PARAMETER_METHODINPUT1 = "input1";
    public static final String PARAMETER_METHODINPUT2 = "input2";

    public static final String DEFAULT_URL = "http://${ipaddr}:${port}/jolokia";
    private static final Logger LOGGER = LoggerFactory.getLogger(JolokiaBeanMonitor.class);
    /**
     * {@inheritDoc}
     *
     * Poll the specified address for service availability.
     *
     * During the poll an attempt is made to execute the named method (with
     * optional input) connect on the specified port. If the exec on request is
     * successful, the banner line generated by the interface is parsed and if
     * the banner text indicates that we are talking to Provided that the
     * interface's response is valid we set the service status to
     * SERVICE_AVAILABLE and return.
     */
    public PollStatus poll(MonitoredService svc, Map<String, Object> parameters) {
        //
        // Process parameters
        //
        //
        TimeoutTracker tracker = new TimeoutTracker(parameters, DEFAULT_RETRY, DEFAULT_TIMEOUT);

        // Port
        int port = ParameterMap.getKeyedInteger(parameters, PARAMETER_PORT, DEFAULT_PORT);

        //URL
        String strURL = ParameterMap.getKeyedString(parameters, PARAMETER_URL, DEFAULT_URL);

        //Username
        String strUser = ParameterMap.getKeyedString(parameters, PARAMETER_USERNAME, null);

        //Password
        String strPasswd = ParameterMap.getKeyedString(parameters, PARAMETER_PASSWORD, null);

        //AttrName
        String strAttrName = ParameterMap.getKeyedString(parameters, PARAMETER_ATTRNAME, null);

        //AttrPath
        String strAttrPath = ParameterMap.getKeyedString(parameters, PARAMETER_ATTRPATH, null);

        //BeanName
        String strBeanName = ParameterMap.getKeyedString(parameters, PARAMETER_BEANNAME, null);

        //MethodName
        String strMethodName = ParameterMap.getKeyedString(parameters, PARAMETER_METHODNAME, null);

        //Optional Inputs
        String strInput1 = ParameterMap.getKeyedString(parameters, PARAMETER_METHODINPUT1, null);
        String strInput2 = ParameterMap.getKeyedString(parameters, PARAMETER_METHODINPUT2, null);

        // BannerMatch
        String strBannerMatch = ParameterMap.getKeyedString(parameters, PARAMETER_BANNER, null);

        // Get the address instance.
        InetAddress ipAddr = svc.getAddress();

        final String hostAddress = InetAddressUtils.str(ipAddr);

        LOGGER.debug("poll: address = " + hostAddress + ", port = " + port + ", " + tracker);

        strURL = strURL.replace("${ipaddr}", hostAddress);
        strURL = strURL.replace("${port}", ((Integer) port).toString());

        LOGGER.debug("poll: final URL address = " + strURL);

        // Give it a whirl
        PollStatus serviceStatus = PollStatus.unknown("Initialized");

        for (tracker.reset(); tracker.shouldRetry() && !serviceStatus.isAvailable(); tracker.nextAttempt()) {
            try {
                tracker.startAttempt();
                J4pClientBuilder j4pClientBuilder = new J4pClientBuilder();
                j4pClientBuilder.url(strURL).connectionTimeout(tracker.getConnectionTimeout()).socketTimeout(tracker.getSoTimeout());

                if (strUser != null && strPasswd != null) {
                    j4pClientBuilder.user(strUser).password(strPasswd);
                }

                J4pClient j4pClient = j4pClientBuilder.build();

                LOGGER.debug("JolokiaBeanMonitor: connected to URLhost: " + strURL);

                // We're connected, so upgrade status to unresponsive
                serviceStatus = PollStatus.unresponsive();

                if (strBannerMatch == null || strBannerMatch.length() == 0 || strBannerMatch.equals("*")) {
                    serviceStatus = PollStatus.available(tracker.elapsedTimeInMillis());
                    break;
                }

                //Exec a method or poll an attribute?
                String response;

                if (strAttrName != null) {
                    J4pReadRequest readReq = new J4pReadRequest(strBeanName, strAttrName);
                    readReq.setPreferredHttpMethod("POST");
                    if (strAttrPath != null) {
                        readReq.setPath(strAttrPath);
                    }

                    J4pReadResponse resp = j4pClient.execute(readReq);
                    response = resp.getValue().toString();
                } else {
                    J4pExecRequest execReq;

                    //Default Inputs
                    if (strInput1 == null && strInput2 == null) {
                        LOGGER.debug("JolokiaBeanMonitor - execute bean: " + strBeanName + " method: " + strMethodName);
                        execReq = new J4pExecRequest(strBeanName, strMethodName);
                    } else if (strInput1 != null && strInput2 == null) {
                        //Single Input
                        LOGGER.debug("JolokiaBeanMonitor - execute bean: " + strBeanName + " method: " + strMethodName + " args: " + strInput1);
                        execReq = new J4pExecRequest(strBeanName, strMethodName, strInput1);
                    } else {
                        //Double Input
                        LOGGER.debug("JolokiaBeanMonitor - execute bean: " + strBeanName + " method: " + strMethodName + " args: " + strInput1 + " " + strInput2);
                        execReq = new J4pExecRequest(strBeanName, strMethodName, strInput1, strInput2);
                    }

                    execReq.setPreferredHttpMethod("POST");
                    J4pExecResponse resp = j4pClient.execute(execReq);
                    response = resp.getValue().toString();
                }

                double responseTime = tracker.elapsedTimeInMillis();

                if (response == null) {
                    continue;
                }

                LOGGER.debug("poll: banner = " + response);
                LOGGER.debug("poll: responseTime = " + responseTime + "ms");

                //Could it be a regex?
                if (strBannerMatch.charAt(0) == '~') {
                    if (!response.matches(strBannerMatch.substring(1))) {
                        serviceStatus = PollStatus.unavailable("Banner does not match Regex '" + strBannerMatch + "'");
                    } else {
                        serviceStatus = PollStatus.available(responseTime);
                    }
                } else {
                    if (response.contains(strBannerMatch)) {
                        serviceStatus = PollStatus.available(responseTime);
                    } else {
                        serviceStatus = PollStatus.unavailable("Did not find expected Text '" + strBannerMatch + "'");
                    }
                }

            } catch (J4pConnectException e) {
                String reason = "Connection exception for address: " + ipAddr + ":" + port + " " + e.getMessage();
                LOGGER.debug(reason, e);
                serviceStatus = PollStatus.unavailable(reason);
                break;
            } catch (J4pRemoteException e) {
                String reason = "Remote exception from J4pRemote: "+ e.getMessage();
                LOGGER.debug(reason, e);
                serviceStatus = PollStatus.unavailable(reason);
            } catch (MalformedObjectNameException e) {
                String reason = "Parameters for Jolokia are malformed: "+ e.getMessage();
                LOGGER.debug(reason, e);
                serviceStatus = PollStatus.unavailable(reason);
            } catch (J4pException e) {
                String reason = J4pException.class.getSimpleName() + " during Jolokia monitor call: "+ e.getMessage();
                LOGGER.debug(reason, e);
                serviceStatus = PollStatus.unavailable(reason);
            }
        }

        return serviceStatus;
    }
}
