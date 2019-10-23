import {
  AppBar,
  Box,
  Button,
  CircularProgress,
  ClickAwayListener,
  createStyles,
  Divider,
  Fade,
  Icon,
  IconButton,
  InputBase,
  Menu,
  MenuItem,
  MenuList,
  Paper,
  Popper,
  Slide,
  Theme,
  Toolbar as MUIToolbar,
  Typography,
  WithStyles,
  withStyles,
  withWidth,
} from '@material-ui/core';
import { fade } from '@material-ui/core/styles/colorManipulator';
import {
  AccountCircleOutlined,
  ArrowDropDown,
  ArrowDropUp,
  ChevronRight,
  Close,
  Menu as MenuIcon,
  Search as SearchIcon,
} from '@material-ui/icons';
import clsx from 'clsx';
import _ from 'lodash';
import * as R from 'ramda';
import React, { Component } from 'react';
import { connect } from 'react-redux';
import { RouteComponentProps, withRouter } from 'react-router-dom';
import { bindActionCreators, Dispatch } from 'redux';
import { logout } from '../actions/auth';
import { search, SearchInitiatedPayload } from '../actions/search';
import RouterLink, { StdRouterLink } from '../components/RouterLink';
import { AppState } from '../reducers';
import { Genre as GenreModel } from '../types';
import { getTmdbPosterImage } from '../utils/image-helper';
import { truncateText } from '../utils/textHelper';
import { ApiItem } from '../types/v2';

const styles = (theme: Theme) =>
  createStyles({
    appbar: {
      zIndex: 99999,
    },
    root: {
      flexGrow: 1,
    },
    genreMenu: { columns: 3 },
    grow: {
      flexGrow: 1,
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
    noResults: {
      margin: theme.spacing(1),
      alignSelf: 'center',
    },
    poster: {
      width: 25,
      boxShadow: '7px 10px 23px -8px rgba(0,0,0,0.57)',
      marginRight: 8,
    },
    progressSpinner: {
      margin: theme.spacing(1),
      justifySelf: 'center',
    },
    searchClear: {
      color: theme.palette.common.white,
      opacity: 0.25,
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
    searchIcon: {
      width: theme.spacing(9),
      height: '100%',
      position: 'absolute',
      pointerEvents: 'none',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
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
  });

interface OwnProps extends WithStyles<typeof styles> {
  currentSearchText?: string;
  genres?: GenreModel[];
  isAuthed: boolean;
  isSearching: boolean;
  onDrawerChange: () => void;
  searchResults?: ApiItem[];
  drawerOpen: boolean;
}

interface WidthProps {
  width: string;
}

interface DispatchProps {
  logout: () => void;
  search: (payload: SearchInitiatedPayload) => void;
}

type Props = DispatchProps & OwnProps & RouteComponentProps & WidthProps;

interface State {
  anchorEl: HTMLInputElement | null;
  genreAnchorEl: HTMLButtonElement | null;
  genreType: 'movie' | 'show' | null;
  isLoggedOut: boolean;
  mobileSearchBarOpen: boolean;
  searchAnchor: HTMLInputElement | null;
  searchText: string;
}

interface MenuItemProps {
  to: string;
  primary?: string;
  button?: boolean;
  key?: string;
  selected?: boolean;
  listLength?: number;
  onClick?: (event: React.MouseEvent<HTMLElement>) => void;
}

class Toolbar extends Component<Props, State> {
  private mobileSearchInput: React.RefObject<HTMLInputElement>;
  private desktopSearchInput: React.RefObject<HTMLInputElement>;
  private genreAnchorRef: React.RefObject<HTMLButtonElement>;
  private genreShowContainerRef: React.RefObject<HTMLElement>;
  private genreMovieContainerRef: React.RefObject<HTMLElement>;

  constructor(props) {
    super(props);
    this.mobileSearchInput = React.createRef();
    this.desktopSearchInput = React.createRef();
    this.genreAnchorRef = React.createRef();
    this.genreShowContainerRef = React.createRef();
    this.genreMovieContainerRef = React.createRef();
  }

  state = {
    anchorEl: null,
    genreAnchorEl: null,
    genreType: null,
    searchText: '',
    searchAnchor: null,
    mobileSearchBarOpen: false,
    isLoggedOut: true,
  };

  clearSearch = () => {
    let searchText = '';
    this.setState({ searchText });
    this.mobileSearchInput.current && this.mobileSearchInput.current.focus();
  };

  handleSearchChangeDebounced = _.debounce((target, searchText) => {
    if (
      this.state.searchAnchor === null &&
      this.props.location.pathname !== '/search'
    ) {
      this.setState({ searchAnchor: target });
    }

    if (this.props.location.pathname === '/search') {
      this.setState({ searchAnchor: null });
    }

    if (searchText.length > 0) {
      this.setState({ searchText });
      this.execQuickSearch(searchText);
    }
  }, 250);

  handleSearchChange = event => {
    let target = event.currentTarget;
    let searchText = target.value;
    this.handleSearchChangeDebounced(target, searchText);
  };

  handleSearchFocus = event => {
    if (
      this.state.searchAnchor === null &&
      this.props.location.pathname !== '/search'
    ) {
      this.setState({ searchAnchor: event.currentTarget });
    }
  };

  handleSearchForSubmit = event => {
    this.resetSearchAnchor(event);
    this.execSearch(this.state.searchText);
  };

  handleSearchForEnter = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (event.keyCode === 13) {
      this.execSearch(this.state.searchText);
      event.currentTarget.blur();
      this.setState({ searchAnchor: null });
    }
  };

  resetSearchAnchor = event => {
    // If user is clicking back into search field, don't resetAnchor
    if (event.target !== this.desktopSearchInput.current) {
      this.setState({ searchAnchor: null });
    }
  };

  execSearch = (text: string) => {
    if (this.props.location.pathname !== '/search') {
      this.props.history.push(`/search?q=${encodeURIComponent(text)}`);
    } else {
      this.props.history.push(`?q=${encodeURIComponent(text)}`);
    }

    if (text.length >= 1 && this.props.currentSearchText !== text) {
      this.props.search({
        query: text,
      });
    }
  };

  execQuickSearch = (text: string) => {
    if (text.length >= 1 && this.props.currentSearchText !== text) {
      if (this.props.location.pathname === '/search') {
        this.props.history.push(`?q=${encodeURIComponent(text)}`);
      }

      this.props.search({
        query: text,
      });
    }
  };

  debouncedExecSearch = _.debounce(this.execQuickSearch, 250);

  handleMobileSearchDisplay = () => {
    this.setState(
      state => ({ mobileSearchBarOpen: !this.state.mobileSearchBarOpen }),
      () => {
        this.mobileSearchInput.current &&
          this.mobileSearchInput.current.focus();
      },
    );
  };

  renderQuickSearch() {
    let { searchText, searchAnchor } = this.state;
    let { classes, searchResults, isSearching } = this.props;
    searchResults = searchResults || [];

    return searchAnchor && searchText.length > 0 ? (
      <ClickAwayListener onClickAway={this.resetSearchAnchor}>
        <Popper
          open={!!searchAnchor}
          anchorEl={searchAnchor}
          placement="bottom"
          keepMounted
          transition
          disablePortal
        >
          {({ TransitionProps, placement }) => (
            <Fade
              {...TransitionProps}
              style={{
                transformOrigin:
                  placement === 'bottom' ? 'center top' : 'center bottom',
              }}
              in={!!searchAnchor}
            >
              <Paper
                id="menu-list-grow"
                style={{
                  height: 'auto',
                  overflow: 'scroll',
                  width: 288,
                }}
              >
                <MenuList
                  style={
                    isSearching
                      ? { display: 'flex', justifyContent: 'center' }
                      : {}
                  }
                >
                  {isSearching ? (
                    <CircularProgress className={classes.progressSpinner} />
                  ) : (
                    <React.Fragment>
                      {searchResults!.length ? (
                        searchResults!.slice(0, 5).map(result => {
                          return (
                            <MenuItem
                              dense
                              component={RouterLink}
                              to={`/${result.type}/${result.slug}`}
                              key={result.id}
                              onClick={this.resetSearchAnchor}
                            >
                              <img
                                src={
                                  getTmdbPosterImage(result)
                                    ? `https://image.tmdb.org/t/p/w92/${
                                        getTmdbPosterImage(result)!.id
                                      }`
                                    : ''
                                }
                                className={classes.poster}
                              />
                              {truncateText(result.original_title, 30)}
                            </MenuItem>
                          );
                        })
                      ) : (
                        <Typography
                          variant="body1"
                          gutterBottom
                          align="center"
                          className={classes.noResults}
                        >
                          No results :(
                        </Typography>
                      )}
                      {searchResults!.length > 5 && (
                        <MenuItem
                          dense
                          style={{ justifyContent: 'center' }}
                          onClick={this.handleSearchForSubmit}
                        >
                          View All Results
                        </MenuItem>
                      )}
                    </React.Fragment>
                  )}
                </MenuList>
              </Paper>
            </Fade>
          )}
        </Popper>
      </ClickAwayListener>
    ) : null;
  }

  handleMenu = event => {
    this.setState({ anchorEl: event.currentTarget });
  };

  handleClose = () => {
    this.setState({ anchorEl: null });
  };

  handleGenreMenu = (event, type: 'movie' | 'show' | null) => {
    // If user is on smaller device, go directly to page
    if (['xs', 'sm', 'md'].includes(this.props.width)) {
      this.props.history.push(`Popular?type=${type}`);
      return;
    }
    // If Genre menu is already open and user is not navigating to submenu, close it
    // event.relatedTarget is target element in a mouseEnter/mouseExit event
    if (
      this.state.genreType === type &&
      event.relatedTarget !==
        this.genreMovieContainerRef!.current!.firstChild &&
      event.relatedTarget !== this.genreShowContainerRef!.current!.firstChild
    ) {
      this.setState({
        genreAnchorEl: null,
        genreType: null,
      });
      return;
    }
    this.setState({
      genreAnchorEl: event.currentTarget,
      genreType: type,
    });
  };

  handleGenreMenuClose = event => {
    this.setState({
      genreAnchorEl: null,
      genreType: null,
    });
  };

  handleLogout = () => {
    this.handleClose();
    this.props.logout();
    this.setState({
      isLoggedOut: true,
    });
  };

  toggleDrawer = () => {
    this.props.onDrawerChange();
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
              inputRef={this.desktopSearchInput}
              onChange={this.handleSearchChange}
              onKeyDown={this.handleSearchForEnter}
              onFocus={this.handleSearchFocus}
            />
          </div>
          <div className={classes.grow} />
          {this.renderQuickSearch()}
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

  renderGenreMenu(type: 'movie' | 'show') {
    const { classes, genres, width } = this.props;
    const { genreAnchorEl } = this.state;
    let columns;

    if (width === 'lg') {
      columns = 3;
    } else if (width === 'md') {
      columns = 2;
    } else {
      columns = 1;
    }

    // Todo: support 'show' in genre types
    const filteredGenres =
      (genres &&
        genres.filter(item =>
          item.type.includes(type === 'show' ? 'tv' : 'movie'),
        )) ||
      [];

    function MenuItemLink(props: MenuItemProps) {
      const { primary, to, selected, onClick } = props;

      return (
        <MenuItem
          button
          component={RouterLink}
          to={to}
          selected={selected}
          onClick={onClick}
          dense
        >
          {primary}
        </MenuItem>
      );
    }

    return (
      <React.Fragment>
        <Button
          aria-controls="genre-menu"
          aria-haspopup="true"
          onClick={event => this.handleGenreMenu(event, type)}
          onMouseEnter={event => this.handleGenreMenu(event, type)}
          onMouseLeave={event => this.handleGenreMenu(event, type)}
          style={{
            backgroundColor:
              this.state.genreType === type ? '#424242' : 'inherit',
            borderBottomLeftRadius: 0,
            borderBottomRightRadius: 0,
          }}
          endIcon={
            ['xs', 'sm', 'md'].includes(this.props.width) ? null : this.state
                .genreType === type ? (
              <ArrowDropUp />
            ) : (
              <ArrowDropDown />
            )
          }
        >
          {type === 'show'
            ? ['xs', 'sm', 'md'].includes(this.props.width)
              ? 'shows'
              : 'TV Shows'
            : 'Movies'}
        </Button>
        <Popper
          open={Boolean(this.state.genreType === type)}
          anchorEl={genreAnchorEl}
          placement="bottom-start"
          keepMounted
          transition
          disablePortal
        >
          {({ TransitionProps }) => (
            <Fade
              {...TransitionProps}
              in={Boolean(this.state.genreType === type)}
              timeout={200}
            >
              <Paper
                style={{
                  position: 'absolute',
                  zIndex: 9999999,
                  marginTop: 0,
                  borderTopLeftRadius: 0,
                }}
                onMouseLeave={this.handleGenreMenuClose}
                ref={
                  type === 'show'
                    ? this.genreShowContainerRef
                    : this.genreMovieContainerRef
                }
              >
                <ClickAwayListener
                  onClickAway={this.handleGenreMenuClose}
                  touchEvent={false}
                >
                  <MenuList
                    style={{
                      display: 'flex',
                      flexFlow: 'column wrap',
                      height: 275,
                      width: 475,
                    }}
                  >
                    <Typography
                      variant="subtitle1"
                      style={{ fontWeight: 700, padding: '6px 16px' }}
                    >
                      Explore
                    </Typography>
                    <Divider />
                    <MenuItemLink
                      onClick={this.handleGenreMenuClose}
                      key={`popular-${type}`}
                      to={`/popular?type=${type}`}
                      primary={"What's Popular?"}
                    />
                    <MenuItemLink
                      onClick={this.handleGenreMenuClose}
                      key={`new-${type}`}
                      to={`/new?type=${type}`}
                      primary={"What's New?"}
                    />
                    <Typography
                      variant="subtitle1"
                      style={{ fontWeight: 700, padding: '6px 16px' }}
                    >
                      Genres
                    </Typography>
                    <Divider />
                    {filteredGenres.map(item => {
                      return (
                        <MenuItemLink
                          onClick={this.handleGenreMenuClose}
                          key={item.slug}
                          to={`/genres/${item.slug}?type=${type}`}
                          primary={item.name}
                        />
                      );
                    })}
                  </MenuList>
                </ClickAwayListener>
              </Paper>
            </Fade>
          )}
        </Popper>
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
    let { classes, drawerOpen, isAuthed } = this.props;
    let { searchText, isLoggedOut } = this.state;

    function ButtonLink(props) {
      const { primary, to } = props;

      return (
        <Button component={RouterLink} to={to} color="inherit">
          {primary}
        </Button>
      );
    }

    return (
      <AppBar position="sticky" style={{ whiteSpace: 'nowrap' }}>
        <MUIToolbar variant="dense" disableGutters>
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
          <div className={classes.grow} />
          {this.renderGenreMenu('show')}
          {this.renderGenreMenu('movie')}
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
          {/* Todo: decide how to handle this for desktop/mobile */}
          {/* {!isAuthed ? (
            <React.Fragment>
              <Hidden lgUp>
                <IconButton component={RouterLink} to="/login">
                  <Person />
                </IconButton>
              </Hidden>
              <Hidden mdDown>
                <ButtonLink primary="Login" to="/login" />
                <ButtonLink primary="Signup" to="/signup" />
              </Hidden>
            </React.Fragment>
          ) : null} */}
          {this.renderProfileMenu()}
        </MUIToolbar>
      </AppBar>
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
    genres: appState.metadata.genres,
    isSearching: appState.search.searching,
    searchResults: appState.search.results,
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

export default withWidth()(
  withRouter(
    withStyles(styles)(
      connect(
        mapStateToProps,
        mapDispatchToProps,
      )(Toolbar),
    ),
  ),
);
