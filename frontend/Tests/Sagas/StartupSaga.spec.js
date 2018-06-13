const stepper = (fn) => (mock) => fn.next(mock).value

test('watches for the right action', () => {
  // How the hell?
  // const step = stepper(startup())
  // UserActions.userRequest('1')
  // expect(step()).toEqual(select(selectAvatar))
  // expect(step()).toEqual(put(GithubActions.userRequest('GantMan')))
})
