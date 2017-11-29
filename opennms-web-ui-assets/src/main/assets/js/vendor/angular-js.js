/* Angular Core */
const angular = require('expose-loader?angular!angular');
require('imports-loader?angular!angular-animate');
require('imports-loader?angular!angular-cookies');
require('imports-loader?angular!angular-route');
require('imports-loader?angular!angular-resource');
require('imports-loader?angular!angular-sanitize');

/* 3rd-Party Modules */
require('imports-loader?angular!angular-growl-v2');
require('imports-loader?angular!angular-loading-bar');

require('angular-growl-v2/build/angular-growl.css');
require('angular-loading-bar/build/loading-bar.css');

/* Bootstrap UI */
require('./bootstrap-js');
require('imports-loader?angular!angular-bootstrap-checkbox');
require('imports-loader?angular!angular-ui-bootstrap');

module.exports = angular;