export const AuthStack = {
    root: {
        stack: {
            id: 'Login',
            children: [
                {
                    component: {
                        name: 'navigation.main.LoginScreen',
                        options: {
                            topBar: {
                                visible: false
                            }
                        }
                    }
                }
            ]
        }
    }
};

export let ListView = {
    component: {
        name: 'navigation.main.ListView',
        options: {
            animated: true,
            topBar: {
                visible: false
            }
        }
    }
}

export let MenuScreen = {
  component: {
      name: 'navigation.main.MenuScreen',
      options: {
          animated: true,
          topBar: {
              visible: false
          }
      }
  }
}

export let DetailView = {
    component: {
        name: 'navigation.main.ItemDetailScreen',
        options: {
            animated: true,
            topBar: {
                visible: false
            }
        }
    }
}

// Initial State of the App stack
export const AppStack = {
    root: {
        stack: {
            id: 'App',
            children: [
                ListView
            ]
        }
    }
};