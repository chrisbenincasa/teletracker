import Enzyme from 'enzyme';
import Adapter from 'enzyme-adapter-react-16';

const { JSDOM } = require('jsdom');

const jsdom = new JSDOM('<!doctype html><html><body></body></html>');
const { window } = jsdom;

function copyProps(src, target) {
  Object.defineProperties(target, {
    ...Object.getOwnPropertyDescriptors(src),
    ...Object.getOwnPropertyDescriptors(target),
  });
}

// @ts-ignore
global.window = window;
// @ts-ignore
global.document = window.document;
// @ts-ignore
global.navigator = {
  userAgent: 'node.js',
};
// @ts-ignore
global.requestAnimationFrame = function(callback) {
  return setTimeout(callback, 0);
};
// @ts-ignore
global.cancelAnimationFrame = function(id) {
  clearTimeout(id);
};
copyProps(window, global);

// Configure Enzyme with React 16 adapter
Enzyme.configure({ adapter: new Adapter() });
