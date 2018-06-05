import { sampleFunction } from "../App/tester";
import {} from "jest";

describe("This is a simple test", () => {
  test("Check the sampleFunction function", () => {
    expect(sampleFunction("hello")).toEqual("hellohello");
  });
});