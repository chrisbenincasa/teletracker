module.exports = {
  preset: "react-native",
  transform: {
    "^.+\\.tsx?$": "ts-jest",
    "^.+\\.jsx$": "babel-jest",
    "^.+\\.js$": "babel-jest"
  },
  "setupFiles": [
    "<rootDir>/Tests/Setup"
  ],
  "testPathIgnorePatterns": [
    "/node_modules/",
    "<rootDir>/Tests/Setup.js"
  ],
  "moduleNameMapper": {
    "^.+\\.(jpg|jpeg|png|gif|eot|otf|webp|svg|ttf|woff|woff2|mp4|webm|wav|mp3|m4a|aac|oga)$": "identity-obj-proxy"
  },
  testRegex: "(<rootDir>/Tests/.*|(\\.|/)(test|spec))\\.(jsx?|tsx?)$",
  moduleFileExtensions: ["ts", "tsx", "js", "jsx", "json", "node"],
};