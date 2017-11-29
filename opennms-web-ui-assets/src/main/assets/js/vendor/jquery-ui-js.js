const jQuery = require('vendor/jquery-js');

// jquery-ui base
require('imports-loader?$=vendor%2Fjquery-js!jquery-ui/ui/core');
require('imports-loader?$=vendor%2Fjquery-js!jquery-ui/ui/widget');
require('imports-loader?$=vendor%2Fjquery-js!jquery-ui/ui/widgets/mouse');
require('imports-loader?$=vendor%2Fjquery-js!jquery-ui/ui/widgets/draggable');
require('imports-loader?$=vendor%2Fjquery-js!jquery-ui/ui/widgets/droppable');
require('imports-loader?$=vendor%2Fjquery-js!jquery-ui/ui/widgets/resizable');
require('imports-loader?$=vendor%2Fjquery-js!jquery-ui/ui/widgets/selectable');
require('imports-loader?$=vendor%2Fjquery-js!jquery-ui/ui/widgets/sortable');
require('imports-loader?$=vendor%2Fjquery-js!jquery-ui/ui/effect');

// additional core plugins
require('imports-loader?$=vendor%2Fjquery-js!jquery-ui/ui/data');
require('imports-loader?$=vendor%2Fjquery-js!jquery-ui/ui/disable-selection');
require('imports-loader?$=vendor%2Fjquery-js!jquery-ui/ui/escape-selector');
require('imports-loader?$=vendor%2Fjquery-js!jquery-ui/ui/focusable');
require('imports-loader?$=vendor%2Fjquery-js!jquery-ui/ui/form-reset-mixin');
require('imports-loader?$=vendor%2Fjquery-js!jquery-ui/ui/form');
require('imports-loader?$=vendor%2Fjquery-js!jquery-ui/ui/ie');
require('imports-loader?$=vendor%2Fjquery-js!jquery-ui/ui/jquery-1-7');
require('imports-loader?$=vendor%2Fjquery-js!jquery-ui/ui/keycode');
require('imports-loader?$=vendor%2Fjquery-js!jquery-ui/ui/labels');
require('imports-loader?$=vendor%2Fjquery-js!jquery-ui/ui/plugin');
require('imports-loader?$=vendor%2Fjquery-js!jquery-ui/ui/position');
require('imports-loader?$=vendor%2Fjquery-js!jquery-ui/ui/safe-active-element');
require('imports-loader?$=vendor%2Fjquery-js!jquery-ui/ui/safe-blur');
require('imports-loader?$=vendor%2Fjquery-js!jquery-ui/ui/scroll-parent');
require('imports-loader?$=vendor%2Fjquery-js!jquery-ui/ui/tabbable');
require('imports-loader?$=vendor%2Fjquery-js!jquery-ui/ui/unique-id');
require('imports-loader?$=vendor%2Fjquery-js!jquery-ui/ui/version');

// additional widgets
require('imports-loader?$=vendor%2Fjquery-js!jquery-ui/ui/widgets/accordion');
require('imports-loader?$=vendor%2Fjquery-js!jquery-ui/ui/widgets/autocomplete');
require('imports-loader?$=vendor%2Fjquery-js!jquery-ui/ui/widgets/button');
require('imports-loader?$=vendor%2Fjquery-js!jquery-ui/ui/widgets/checkboxradio');
require('imports-loader?$=vendor%2Fjquery-js!jquery-ui/ui/widgets/controlgroup');
require('imports-loader?$=vendor%2Fjquery-js!jquery-ui/ui/widgets/datepicker');
require('imports-loader?$=vendor%2Fjquery-js!jquery-ui/ui/widgets/dialog');
require('imports-loader?$=vendor%2Fjquery-js!jquery-ui/ui/widgets/menu');
require('imports-loader?$=vendor%2Fjquery-js!jquery-ui/ui/widgets/progressbar');
require('imports-loader?$=vendor%2Fjquery-js!jquery-ui/ui/widgets/selectmenu');
require('imports-loader?$=vendor%2Fjquery-js!jquery-ui/ui/widgets/slider');
require('imports-loader?$=vendor%2Fjquery-js!jquery-ui/ui/widgets/spinner');
require('imports-loader?$=vendor%2Fjquery-js!jquery-ui/ui/widgets/tabs');
require('imports-loader?$=vendor%2Fjquery-js!jquery-ui/ui/widgets/tooltip');

// 3rd-party jquery-ui plugins
require('imports-loader?define=>false,$=jquery!jquery-ui-treemap');
require('imports-loader?define=>false,$=jquery!jquery-sparkline/dist/jquery.sparkline');

module.exports = jQuery;