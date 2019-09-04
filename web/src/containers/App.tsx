import {
  AppBar,
  Box,
  Button,
  createStyles,
  CssBaseline,
  Fade,
  Icon,
  IconButton,
  InputBase,
  Menu,
  MenuItem,
  Slide,
  Theme,
  Toolbar,
  Typography,
  WithStyles,
  withStyles,
} from '@material-ui/core';
import { fade } from '@material-ui/core/styles/colorManipulator';
import {
  AccountCircleOutlined,
  ChevronRight,
  Close,
  Menu as MenuIcon,
  Search as SearchIcon,
} from '@material-ui/icons';
import clsx from 'clsx';
import * as R from 'ramda';
import React, { Component } from 'react';
import { connect } from 'react-redux';
import {
  Route,
  RouteComponentProps,
  Switch,
  withRouter,
} from 'react-router-dom';
import { bindActionCreators, Dispatch } from 'redux';
import { logout } from '../actions/auth';
import { search } from '../actions/search';
import { AppState } from '../reducers';
import Search from './Search';
import Account from './Account';
import Home from './Home';
import ListDetail from './ListDetail';
import ItemDetail from './ItemDetail';
import Lists from './Lists';
import Login from './Login';
import Signup from './Signup';
import New from './New';
import Popular from './Popular';
import PersonDetail from './PersonDetail';
import Drawer, { DrawerWidthPx } from '../components/Drawer';
import RouterLink, { StdRouterLink } from '../components/RouterLink';

const styles = (theme: Theme) =>
  createStyles({
    root: {
      flexGrow: 1,
    },
    grow: {
      flexGrow: 1,
    },
    menuButton: {
      marginLeft: -12,
      marginRight: 20,
    },
    search: {
      position: 'relative',
      borderRadius: theme.shape.borderRadius,
      backgroundColor: fade(theme.palette.common.white, 0.15),
      '&:hover': {
        backgroundColor: fade(theme.palette.common.white, 0.25),
      },
      marginRight: theme.spacing(2),
      marginLeft: 0,
      width: '100%',
      [theme.breakpoints.up('sm')]: {
        marginLeft: theme.spacing(3),
        width: 'auto',
      },
      [theme.breakpoints.down('sm')]: {
        display: 'none',
      },
    },
    searchMobile: {
      display: 'flex',
      position: 'relative',
      borderRadius: theme.shape.borderRadius,
      backgroundColor: fade(theme.palette.common.white, 0.15),
      '&:hover': {
        backgroundColor: fade(theme.palette.common.white, 0.25),
      },
      width: '100%',
    },
    mobileSearchContainer: {
      flexGrow: 1,
      position: 'absolute',
      width: '100%',
      backgroundColor: theme.palette.primary[500],
      zIndex: 9999,
      padding: 'inherit',
      left: 0,
      right: 0,
    },
    searchClear: {
      color: theme.palette.common.white,
      opacity: 0.25,
    },
    searchIcon: {
      width: theme.spacing(9),
      height: '100%',
      position: 'absolute',
      pointerEvents: 'none',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
    },
    inputRoot: {
      color: 'inherit',
      width: '100%',
    },
    inputInput: {
      paddingTop: theme.spacing(1),
      paddingRight: theme.spacing(1),
      paddingBottom: theme.spacing(1),
      paddingLeft: theme.spacing(10),
      transition: theme.transitions.create('width'),
      width: '100%',
      [theme.breakpoints.up('md')]: {
        width: 200,
        '&:focus': {
          width: 400,
        },
      },
      '&::-webkit-search-decoration,&::-webkit-search-cancel-button,&::-webkit-search-results-button,&::-webkit-search-results-decoration': {
        '-webkit-appearance': 'none',
      },
      caretColor: theme.palette.common.white,
    },
    mobileInput: {
      padding: theme.spacing(1),
      width: '100%',
      [theme.breakpoints.up('md')]: {
        width: 200,
        '&:focus': {
          width: 400,
        },
      },
      '&::-webkit-search-decoration,&::-webkit-search-cancel-button,&::-webkit-search-results-button,&::-webkit-search-results-decoration': {
        '-webkit-appearance': 'none',
      },
      caretColor: theme.palette.common.white,
    },
    sectionDesktop: {
      display: 'none',
      [theme.breakpoints.up('md')]: {
        display: 'flex',
      },
    },
    sectionMobile: {
      display: 'flex',
      justifyContent: 'flex-end',
      [theme.breakpoints.up('md')]: {
        display: 'none',
      },
    },
    appbar: {
      zIndex: 99999,
    },
    mainContent: {
      transition: theme.transitions.create('margin', {
        easing: theme.transitions.easing.sharp,
        duration: theme.transitions.duration.leavingScreen,
      }),
      marginLeft: 0,
    },
    mainContentShift: {
      transition: theme.transitions.create('margin', {
        easing: theme.transitions.easing.easeOut,
        duration: theme.transitions.duration.enteringScreen,
      }),
      [theme.breakpoints.up('sm')]: {
        marginLeft: DrawerWidthPx,
      },
    },
  });

interface OwnProps extends WithStyles<typeof styles> {
  isAuthed: boolean;
  currentSearchText?: string;
}

interface DispatchProps {
  logout: () => void;
  search: (text: string) => void;
}

type Props = DispatchProps & OwnProps & RouteComponentProps;

interface State {
  anchorEl: any;
  searchText: string;
  mobileSearchBarOpen: boolean;
  drawerOpen: boolean;
  isLoggedOut: boolean;
}

interface MenuItemProps {
  to: any;
  primary?: string;
  button?: any;
  key?: any;
  selected?: any;
  listLength?: number;
  onClick?: any;
}

class App extends Component<Props, State> {
  private mobileSearchInput: React.RefObject<HTMLInputElement>;
  constructor(props) {
    super(props);
    this.mobileSearchInput = React.createRef();
  }

  state = {
    anchorEl: null,
    searchText: '',
    mobileSearchBarOpen: false,
    drawerOpen: false,
    isLoggedOut: true,
  };

  clearSearch = () => {
    let searchText = '';
    this.setState({ searchText });
    this.mobileSearchInput.current && this.mobileSearchInput.current.focus();
  };

  handleSearchChange = event => {
    let searchText = event.currentTarget.value;
    this.setState({ searchText });
  };

  handleSearchForSubmit = () => {
    this.execSearch(this.state.searchText);
  };

  handleSearchForEnter = (ev: React.KeyboardEvent<HTMLInputElement>) => {
    if (ev.keyCode === 13) {
      this.execSearch(this.state.searchText);
      ev.currentTarget.blur();
    }
  };

  execSearch = (text: string) => {
    if (this.props.location.pathname !== '/search') {
      this.props.history.push('/search');
    }

    if (text.length >= 1 && this.props.currentSearchText !== text) {
      this.props.search(text);
    }
  };

  handleMobileSearchDisplay = () => {
    this.setState(
      state => ({ mobileSearchBarOpen: !this.state.mobileSearchBarOpen }),
      () => {
        this.mobileSearchInput.current &&
          this.mobileSearchInput.current.focus();
      },
    );
  };

  handleMenu = event => {
    this.setState({ anchorEl: event.currentTarget });
  };

  handleClose = () => {
    this.setState({ anchorEl: null });
  };

  handleLogout = () => {
    this.handleClose();
    this.props.logout();
    this.setState({
      isLoggedOut: true,
    });
  };

  toggleDrawer = () => {
    this.setState({ drawerOpen: !this.state.drawerOpen });
  };

  renderSearch() {
    let { classes } = this.props;
    let { mobileSearchBarOpen } = this.state;

    return (
      <React.Fragment>
        <div className={classes.sectionDesktop}>
          <div className={classes.search}>
            <div className={classes.searchIcon}>
              <SearchIcon />
            </div>
            <InputBase
              placeholder="Search&hellip;"
              classes={{
                root: classes.inputRoot,
                input: classes.inputInput,
              }}
              type="search"
              inputProps={{
                'aria-label': 'search Teletracker',
                inputMode: 'search',
              }}
              onChange={this.handleSearchChange}
              onKeyDown={this.handleSearchForEnter}
            />
          </div>
          <div className={classes.grow} />
        </div>
        {!mobileSearchBarOpen ? (
          <div className={classes.sectionMobile}>
            <IconButton
              aria-owns={mobileSearchBarOpen ? 'material-appbar' : undefined}
              aria-haspopup="true"
              onClick={this.handleMobileSearchDisplay}
              color="inherit"
              style={
                mobileSearchBarOpen
                  ? { backgroundColor: 'rgba(250,250,250, 0.15)' }
                  : undefined
              }
            >
              <SearchIcon />
            </IconButton>{' '}
          </div>
        ) : null}
      </React.Fragment>
    );
  }

  renderProfileMenu() {
    if (!this.props.isAuthed) {
      return null;
    }

    let { anchorEl } = this.state;
    let { classes } = this.props;
    let isMenuOpen = !!anchorEl;

    function MenuItemLink(props: MenuItemProps) {
      const { primary, to, selected, onClick } = props;

      return (
        <MenuItem
          button
          component={RouterLink}
          to={to}
          selected={selected}
          onClick={onClick}
        >
          {primary}
        </MenuItem>
      );
    }

    return (
      <div className={classes.sectionDesktop}>
        <IconButton
          aria-owns={isMenuOpen ? 'material-appbar' : undefined}
          aria-haspopup="true"
          onClick={this.handleMenu}
          color="inherit"
        >
          <AccountCircleOutlined />
        </IconButton>
        <Menu
          anchorEl={anchorEl}
          anchorOrigin={{ vertical: 'top', horizontal: 'right' }}
          transformOrigin={{ vertical: 'top', horizontal: 'right' }}
          open={!!this.state.anchorEl}
          onClose={this.handleClose}
          disableAutoFocusItem
        >
          <MenuItem onClick={this.handleClose}>Profile</MenuItem>
          <MenuItemLink
            to="/account"
            onClick={this.handleClose}
            primary="My account"
          />
          <MenuItem onClick={this.handleLogout}>Logout</MenuItem>
        </Menu>
      </div>
    );
  }

  render() {
    let { classes, isAuthed } = this.props;
    let { drawerOpen, searchText, isLoggedOut } = this.state;

    if (isLoggedOut) {
      // return <Redirect to={'/login'} />
    }

    function ButtonLink(props) {
      const { primary, to } = props;

      return (
        <Button component={RouterLink} to={to} color="inherit">
          {primary}
        </Button>
      );
    }

    return (
      <div className={classes.root}>
        <CssBaseline />
        <AppBar position="sticky">
          <Toolbar variant="dense">
            <Slide
              direction="left"
              in={this.state.mobileSearchBarOpen}
              timeout={350}
              mountOnEnter
              unmountOnExit
            >
              <div
                className={clsx(
                  classes.sectionMobile,
                  classes.mobileSearchContainer,
                )}
              >
                <IconButton
                  onClick={this.handleMobileSearchDisplay}
                  color="inherit"
                  size="small"
                >
                  <ChevronRight />
                </IconButton>
                <div className={classes.searchMobile}>
                  <InputBase
                    placeholder="Search&hellip;"
                    inputProps={{
                      'aria-label': 'search Teletracker',
                      inputmode: 'search',
                    }}
                    classes={{
                      root: classes.inputRoot,
                      input: classes.mobileInput,
                    }}
                    onChange={this.handleSearchChange}
                    onKeyDown={this.handleSearchForEnter}
                    inputRef={this.mobileSearchInput}
                    type="search"
                    value={searchText}
                  />
                  {searchText.length > 0 ? (
                    <Fade in={true}>
                      <IconButton
                        onClick={this.clearSearch}
                        color="inherit"
                        size="small"
                      >
                        <Close className={classes.searchClear} />
                      </IconButton>
                    </Fade>
                  ) : null}
                </div>
                <div className={classes.searchIcon} />
                <IconButton
                  onClick={this.handleSearchForSubmit}
                  color="inherit"
                  size="small"
                >
                  <SearchIcon />
                </IconButton>
              </div>
            </Slide>
            <IconButton
              focusRipple={false}
              onClick={this.toggleDrawer}
              color="inherit"
            >
              {drawerOpen ? <Icon>menu_open</Icon> : <MenuIcon />}
            </IconButton>
            <Typography
              variant="h6"
              color="inherit"
              className={classes.grow}
              component={props =>
                StdRouterLink('/', {
                  ...props,
                  style: { textDecoration: 'none' },
                })
              }
            >
              <Box display={{ xs: 'none', sm: 'block' }} m={1}>
                Teletracker
              </Box>
              <Box display={{ xs: 'block', sm: 'none' }} m={1}>
                TT
              </Box>
            </Typography>

            <Box display={{ xs: 'none', sm: 'block' }} m={1}>
              <ButtonLink
                color="inherit"
                primary="New, Arriving, &amp; Expiring"
                to="/new"
              />
            </Box>
            <Box display={{ xs: 'block', sm: 'none' }} m={1}>
              <ButtonLink color="inherit" primary="New" to="/new" />
            </Box>
            {this.renderSearch()}
            {!isAuthed ? (
              <React.Fragment>
                <ButtonLink primary="Login" to="/login" />
                <ButtonLink primary="Signup" to="/signup" />
              </React.Fragment>
            ) : null}
            {this.renderProfileMenu()}
          </Toolbar>
        </AppBar>
        <div>
          {/* TODO: investigate better solution for flexDirection issue as it relates to the LinearProgress bar display */}
          <Drawer open={this.state.drawerOpen} />
          <main
            style={{
              display: 'flex',
              flexDirection: isAuthed ? 'row' : 'column',
            }}
            className={clsx(classes.mainContent, {
              [classes.mainContentShift]: this.state.drawerOpen,
            })}
          >
            <Switch>
              <Route exact path="/" render={props => <Popular {...props} />} />
              <Route
                exact
                path="/search"
                render={props => <Search {...props} />}
              />
              <Route
                exact
                path="/account"
                render={props => <Account {...props} />}
              />
              <Route exact path="/login" component={Login} />
              <Route exact path="/signup" component={Signup} />
              <Route exact path="/new" render={props => <New {...props} />} />
              <Route exact path="/popular" component={Popular} />
              {/* <Route exact path="/logout" component={this.handleLogout} /> */}
              <Route
                exact
                path="/lists"
                render={props => <Lists {...props} />}
              />
              <Route
                exact
                path="/lists/:id"
                render={props => <ListDetail {...props} />}
              />
              <Route exact path="/person/:id" component={PersonDetail} />
              <Route
                exact
                path="/:type/:id"
                render={props => <ItemDetail {...props} />}
              />
            </Switch>
          </main>
        </div>
      </div>
    );
  }
}

const mapStateToProps = (appState: AppState) => {
  return {
    isAuthed: !R.isNil(R.path(['auth', 'token'], appState)),
    currentSearchText: R.path<string>(
      ['search', 'currentSearchText'],
      appState,
    ),
  };
};

const mapDispatchToProps: (dispatch: Dispatch) => DispatchProps = dispatch => {
  return bindActionCreators(
    {
      logout,
      search,
    },
    dispatch,
  );
};

export default withRouter(
  withStyles(styles)(
    connect(
      mapStateToProps,
      mapDispatchToProps,
    )(App),
  ),
);
