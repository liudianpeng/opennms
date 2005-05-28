//
// This file is part of the OpenNMS(R) Application.
//
// OpenNMS(R) is Copyright (C) 2005 The OpenNMS Group, Inc.  All rights reserved.
// OpenNMS(R) is a derivative work, containing both original code, included code and modified
// code that was published under the GNU General Public License. Copyrights for modified 
// and included code are below.
//
// OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
//
// Original code base Copyright (C) 1999-2001 Oculan Corp.  All rights reserved.
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
//
// For more information contact:
// OpenNMS Licensing       <license@opennms.org>
//     http://www.opennms.org/
//     http://www.opennms.com/
//
package org.opennms.netmgt.collectd;

import junit.framework.TestCase;

public class SnmpObjIdTest extends TestCase {

    private void assertArrayEquals(int[] a, int[] b) {
        if (a == null) {
            assertNull("expected value is null but actual value is "+b, b);
        } else {
            if (b == null) SnmpCollectionTrackerTest.fail("Expected value is "+a+" but actual value is null"); 
            assertEquals("arrays have different length", a.length, b.length);
            for(int i = 0; i < a.length; i++) {
                assertEquals("array differ at index "+i+" expected: "+a+", actual: "+b, a[i], b[i]);
            }
        }
    }

    public void testSnmpOidCompare() {
        SnmpObjId oid1 = SnmpObjId.get("1.3.5.7");
        SnmpObjId oid1b = SnmpObjId.get(".1.3.5.7");
        SnmpObjId oid2 = SnmpObjId.get(new int[] {1, 3, 5, 7});
        SnmpObjId oid3 = SnmpObjId.get(".1.3.5.8");
        SnmpObjId oid4 = SnmpObjId.get(".1.3.5.7.0");
        
        assertArrayEquals(oid1.getIds(), oid2.getIds());
        
        assertEquals(oid1, oid1b);
        
        assertEquals(".1.3.5.7", oid1.toString());
        
        assertEquals(oid1, oid2);
        
        assertFalse(oid1.equals(oid3));
        assertFalse(oid1.equals(oid4));
        
        assertTrue(oid1.compareTo(oid3) < 0);
        assertTrue(oid3.compareTo(oid1) > 0);
        assertTrue(oid1.compareTo(oid4) < 0);
        assertTrue(oid4.compareTo(oid1) > 0);
        
    }

    public void testOidAppendPrefixInstance() {
        SnmpObjId base = SnmpObjId.get(".1.3.5.7");
        SnmpObjId result = SnmpObjId.get(".1.3.5.7.9.8.7.6");

        assertEquals(result, base.append(new int[] {9,8,7,6}));
        assertEquals(result, base.append("9.8.7.6"));
        
        assertTrue(base.isPrefixOf(base));        assertTrue(base.isPrefixOf(result));
        assertFalse(result.isPrefixOf(base));
        
        SnmpInstId instance = result.getInstance(base);
        assertEquals(SnmpObjId.get(".9.8.7.6"), instance);
        assertEquals("9.8.7.6", instance.toString());
    }
    
    public void testDecrement() {
        SnmpObjId oid = SnmpObjId.get(".1.3.5.7");
        assertEquals(SnmpObjId.get(".1.3.5.6"), oid.decrement());
        
        SnmpObjId oid2 = SnmpObjId.get(".1.3.5.7.0");
        assertEquals(oid, oid2.decrement());
    }
    
    

}
