var webpack = require('webpack');
var path = require('path');
var TypedocWebpackPlugin = require('typedoc-webpack-plugin');
var pkginfo = require('./package.json');

var createVariants = require('parallel-webpack').createVariants;

var clonedeep = require('lodash.clonedeep');

var argv = require('yargs').argv;
var isProduction = argv.env === 'production';

var variants = {
  production: [ false ]
};

if (isProduction) {
  variants.production = [ true, false ];
}

var config = {
  entry: {
    angular: __dirname + '/src/vendor/angular.js'
  },
  devtool: 'source-map',
  output: {
    path: __dirname + '/target/js',
    libraryTarget: 'umd',
    umdNamedDefine: true
  },
  module: {
    rules: [
      {
        test: /\.css$/,
        loader: 'style-loader!css-loader'
      },
      {
        test: /\.scss$/,
        loader: 'style-loader!css-loader!group-css-media-queries-loader!sass-loader'
      },
      {
        enforce: 'pre',
        test: /\.tsx?$/,
        use: [
          {
            loader: 'tslint-loader',
            options: {
              typeCheck: true
            }
          }
        ],
        exclude: [/node_modules/]
      },
      {
        test: /(\.jsx?)$/,
        use: [
          {
            loader: 'babel-loader',
            query: {
              compact: true
            }
          }
        ]
      },
      {
        test: /(\.tsx?)$/,
        use: [
          'babel-loader',
          'ts-loader'
        ],
        exclude: [/node_modules/]
      }
    ]
  },
  resolve: {
    alias: {
      uib: path.join(__dirname, 'node_modules', 'angular-ui-bootstrap')
    },
    modules: [
      path.resolve('./src'),
      path.resolve('./node_modules')
    ],
    extensions: ['.webpack.js', '.web.js', '.ts', '.js']
  },
  plugins: []
};

function createConfig(options) {
  var myconf = clonedeep(config);
  myconf.output.filename = '[name]';
  myconf.target = 'web';
  var defs = {
    IS_PRODUCTION: options.production,
    'global.OPENNMS_VERSION': JSON.stringify(pkginfo.version)
  };

  if (options.production) {
    defs['global.GENTLY'] = false;
  
    myconf.plugins.push(new webpack.LoaderOptionsPlugin({
      minimize: true,
      debug: false
    }));
    myconf.plugins.push(new webpack.optimize.UglifyJsPlugin({
      sourceMap: true,
      mangle: {
        except: [ '$element', '$super', '$', 'jQuery', 'exports', 'require', 'angular', 'ionic', 'ionic-angular' ]
      },
      minimize: true,
      compress: true
    }));
    myconf.output.filename += '.min';
  }
  myconf.output.filename += '.js';

  myconf.plugins.push(new webpack.DefinePlugin(defs));
  myconf.plugins.push(new webpack.ProvidePlugin({X2JS: 'x2js'}));

  console.log('Building variant: production=' + Boolean(options.production)); // eslint-disable-line no-console

  return myconf;
}

module.exports = createVariants({}, variants, createConfig);
