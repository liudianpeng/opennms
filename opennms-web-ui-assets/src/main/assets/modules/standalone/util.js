
export default class Util {
	static getBaseHref() {
		return document.getElementsByTagName('base')[0].href;
	}
	static setLocation(url) {
		window.location.href = Util.getBaseHref() + url;
	}
	static toggle(booleanValue, elementName) {
		var checkboxes = document.getElementsByName(elementName);
		for (var index in checkboxes) {
			if (checkboxes.hasOwnProperty(index)) {
				checkboxes[index].checked = booleanValue;
			}
		}
	}
}