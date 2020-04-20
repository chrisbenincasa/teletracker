import React, { Component, RefObject } from 'react';
import {
  AppBar,
  Box,
  Button,
  ClickAwayListener,
  createStyles,
  Divider,
  Fade,
  Hidden,
  Icon,
  IconButton,
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
  NoSsr,
} from '@material-ui/core';
import {
  ArrowDropDown,
  ArrowDropUp,
  Menu as MenuIcon,
  Person,
  Search as SearchIcon,
  KeyboardArrowUp,
  MenuOpen,
} from '@material-ui/icons';
import clsx from 'clsx';
import _ from 'lodash';
import * as R from 'ramda';
import { connect } from 'react-redux';
import { bindActionCreators, Dispatch } from 'redux';
import { logout } from '../../actions/auth';
import { search, SearchInitiatedPayload } from '../../actions/search';
import Link from 'next/link';
import { WithRouterProps } from 'next/dist/client/with-router';
import { withRouter } from 'next/router';
import { AppState } from '../../reducers';
import { Genre as GenreModel } from '../../types';
import { hexToRGB } from '../../utils/style-utils';
import Search from './Search';
import AuthDialog from '../Auth/AuthDialog';

const styles = (theme: Theme) =>
  createStyles({
    appbar: {
      zIndex: theme.zIndex.drawer + 1,
      whiteSpace: 'nowrap',
    },
    root: {
      flexGrow: 1,
    },
    genreMenuList: {
      display: 'flex',
      flexFlow: 'column wrap',
      height: 275,
      width: 475,
      textTransform: 'capitalize',
    },
    genreMenuSubtitle: {
      fontWeight: theme.typography.fontWeightBold,
      padding: theme.spacing(1, 2),
    },
    genrePaper: {
      // position: 'absolute',
      zIndex: theme.zIndex.appBar,
      marginTop: 10,
      backgroundColor: hexToRGB(theme.palette.primary.main, 0.95),
    },
    grow: {
      flexGrow: 1,
      paddingLeft: theme.spacing(10), // forces search field to be true center
    },
    loginButton: {
      margin: theme.spacing(0, 1),
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
      backgroundColor: theme.palette.primary.main,
      zIndex: theme.zIndex.appBar + 1,
      padding: 'inherit',
      left: 0,
      right: 0,
    },
    mobileSearchIcon: {
      padding: theme.spacing(0.5, 1),
      marginLeft: theme.spacing(2),
    },
    // searchClear: {
    //   color: theme.palette.common.white,
    //   opacity: 0.25,
    // },
    sectionMobile: {
      display: 'flex',
      justifyContent: 'flex-end',
    },
    searchMobile: {
      display: 'flex',
      position: 'relative',
      borderRadius: theme.shape.borderRadius,
      // backgroundColor: fade(theme.palette.common.white, 0.15),
      // '&:hover': {
      //   backgroundColor: fade(theme.palette.common.white, 0.25),
      // },
      marginRight: theme.spacing(2),
      width: '100%',
    },
  });

interface OwnProps extends WithStyles<typeof styles> {
  genres?: GenreModel[];
  isAuthed: boolean;
  onDrawerChange: (close?: boolean) => void;
  drawerOpen: boolean;
  showToolbarSearch: boolean;
  currentSearchText?: string;
  currentQuickSearchText?: string;
}

interface WidthProps {
  width: string;
}

interface DispatchProps {
  logout: () => void;
}

type Props = DispatchProps & OwnProps & WithRouterProps & WidthProps;

interface State {
  genreAnchorEl: HTMLButtonElement | null;
  genreType: 'movie' | 'show' | null;
  isLoggedOut: boolean;
  mobileSearchBarOpen: boolean;
  authDialogOpen: boolean;
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

const MenuItemLink = (props: MenuItemProps) => {
  const { primary, to, selected, onClick } = props;

  const ButtonLink = React.forwardRef((props: any, ref) => {
    let { onClick, href } = props;
    return (
      <Link href={href} passHref>
        <a
          onClick={onClick}
          ref={ref as RefObject<HTMLAnchorElement>}
          {...props}
        >
          {primary}
        </a>
      </Link>
    );
  });

  return (
    <MenuItem
      button
      selected={selected}
      onClick={onClick}
      dense
      href={to}
      component={ButtonLink}
    />
  );
};

class Toolbar extends Component<Props, State> {
  private mobileSearchIcon: React.RefObject<HTMLDivElement>;
  private genreShowContainerRef: React.RefObject<HTMLElement>;
  private genreMovieContainerRef: React.RefObject<HTMLElement>;
  private genreShowSpacerRef: React.RefObject<HTMLDivElement>;
  private genreMovieSpacerRef: React.RefObject<HTMLDivElement>;

  constructor(props) {
    super(props);
    this.mobileSearchIcon = React.createRef();
    this.genreShowContainerRef = React.createRef();
    this.genreMovieContainerRef = React.createRef();
    this.genreShowSpacerRef = React.createRef();
    this.genreMovieSpacerRef = React.createRef();
  }

  state = {
    genreAnchorEl: null,
    genreType: null,
    isLoggedOut: true,
    mobileSearchBarOpen: false,
    authDialogOpen: false,
  };

  get isSmallDevice() {
    return ['xs', 'sm', 'md'].includes(this.props.width);
  }

  handleGenreMenu = (event, type: 'movie' | 'show' | null) => {
    // If quick search is open, close it & move focus to button
    //TODO this.resetSearchAnchor(event);
    event.target.focus();

    // If user is on smaller device, go directly to page
    if (this.isSmallDevice) {
      this.props.router.push(`popular?type=${type}`);
      return;
    }
    // If Genre menu is already open and user is not navigating to submenu, close it
    // event.relatedTarget is target element in a mouseEnter/mouseExit event

    if (
      this.state.genreType === type &&
      event.relatedTarget !==
        this.genreMovieContainerRef!.current!.firstChild &&
      event.relatedTarget !== this.genreShowContainerRef!.current!.firstChild &&
      event.relatedTarget !== this.genreShowSpacerRef!.current! &&
      event.relatedTarget !== this.genreMovieSpacerRef!.current!
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
    if (this.props.drawerOpen) {
      this.props.onDrawerChange();
    }

    this.setState({
      genreAnchorEl: null,
      genreType: null,
    });
  };

  handleLogout = () => {
    this.toggleDrawer(true);
    this.props.logout();
    this.setState({
      isLoggedOut: true,
    });
  };

  toggleDrawer = (close?: boolean) => {
    this.props.onDrawerChange(close);
  };

  renderGenreMenu(type: 'movie' | 'show') {
    const { classes, genres } = this.props;
    const { genreAnchorEl } = this.state;

    // Todo: support 'show' in genre types
    const filteredGenres =
      (genres &&
        genres.filter(item =>
          item.type.includes(type === 'show' ? 'tv' : 'movie'),
        )) ||
      [];

    return (
      <React.Fragment>
        <div style={{ position: 'relative' }}>
          <Button
            aria-controls="genre-menu"
            aria-haspopup="true"
            onClick={event => this.handleGenreMenu(event, type)}
            onMouseEnter={event => this.handleGenreMenu(event, type)}
            onMouseLeave={event => this.handleGenreMenu(event, type)}
            color="inherit"
            focusRipple={false}
            endIcon={
              this.isSmallDevice ? null : this.state.genreType === type ? (
                <ArrowDropUp />
              ) : (
                <ArrowDropDown />
              )
            }
          >
            {type === 'show'
              ? this.isSmallDevice
                ? 'Shows'
                : 'TV Shows'
              : 'Movies'}
          </Button>
          {this.state.genreType === type && (
            <div
              ref={
                type === 'show'
                  ? this.genreShowSpacerRef
                  : this.genreMovieSpacerRef
              }
              style={{
                position: 'absolute',
                bottom: -15,
                height: 15,
                width: '100%',
              }}
            />
          )}
        </div>
        <Popper
          open={Boolean(this.state.genreType === type)}
          anchorEl={genreAnchorEl}
          placement="bottom-end"
          keepMounted
          transition
          style={{
            display: Boolean(this.state.genreType === type) ? 'block' : 'none',
          }}
        >
          {({ TransitionProps }) => (
            <Fade
              {...TransitionProps}
              in={Boolean(this.state.genreType === type)}
              timeout={200}
            >
              <Paper
                className={classes.genrePaper}
                onMouseLeave={this.handleGenreMenuClose}
                ref={
                  type === 'show'
                    ? this.genreShowContainerRef
                    : this.genreMovieContainerRef
                }
              >
                <MenuList className={classes.genreMenuList}>
                  <Typography
                    variant="subtitle1"
                    className={classes.genreMenuSubtitle}
                  >
                    Explore
                  </Typography>
                  <Divider />
                  <MenuItemLink
                    onClick={this.handleGenreMenuClose}
                    to={`/${type}s`}
                    key={`explore-${type}`}
                    primary={`All ${type}s`}
                  />
                  <MenuItemLink
                    onClick={this.handleGenreMenuClose}
                    key={`popular-${type}`}
                    to={`/popular?type=${type}`}
                    primary={`Trending ${type}s`}
                  />
                  <MenuItemLink
                    onClick={this.handleGenreMenuClose}
                    key={`new-${type}`}
                    to={`/new?type=${type}`}
                    primary={`New ${type}s`}
                  />
                  <Typography
                    variant="subtitle1"
                    className={classes.genreMenuSubtitle}
                  >
                    Genres
                  </Typography>
                  <Divider />
                  {filteredGenres.map(item => {
                    return (
                      <MenuItemLink
                        onClick={this.handleGenreMenuClose}
                        key={item.slug}
                        to={`/popular?genres=${item.id}&type=${type}`}
                        primary={item.name}
                      />
                    );
                  })}
                </MenuList>
              </Paper>
            </Fade>
          )}
        </Popper>
      </React.Fragment>
    );
  }

  handleMobileSearchDisplayOpen = () => {
    this.toggleDrawer(true);
    this.setState({ mobileSearchBarOpen: true });
  };

  handleMobileSearchDisplayClose = () => {
    this.setState({ mobileSearchBarOpen: false });
  };

  openAuthDialog = () => this.setState({ authDialogOpen: true });
  closeAuthDialog = () => this.setState({ authDialogOpen: false });

  showQuickSearch = (): boolean => {
    // No need to show quickSearch on the search page if the text is the same.
    // It would just show results that are already present on the page.
    return !(
      this.props.currentQuickSearchText === this.props.currentSearchText &&
      this.props.router.pathname === '/search'
    );
  };

  render() {
    let { classes, drawerOpen, isAuthed } = this.props;
    const { mobileSearchBarOpen } = this.state;

    const ButtonLink = React.forwardRef((props: any, ref) => {
      let { onClick, href, primary } = props;
      return (
        <a
          href={href}
          onClick={onClick}
          ref={ref as RefObject<HTMLAnchorElement>}
          {...props}
        >
          {primary}
        </a>
      );
    });

    return (
      <React.Fragment>
        <AppBar position="sticky">
          <MUIToolbar variant="dense" disableGutters>
            <IconButton
              focusRipple={false}
              onClick={() => this.toggleDrawer()}
              color="inherit"
            >
              {drawerOpen ? <MenuOpen /> : <MenuIcon />}
            </IconButton>
            <Link href="/" passHref>
              <Typography
                variant="h6"
                color="inherit"
                component="a"
                style={{ textDecoration: 'none' }}
              >
                Teletracker
              </Typography>
            </Link>
            <div className={classes.grow}>
              {!this.isSmallDevice && this.props.showToolbarSearch && (
                <Fade in={true} timeout={500}>
                  <Search
                    drawerOpen={drawerOpen}
                    onDrawerChange={this.toggleDrawer}
                    quickSearchEnabled={this.showQuickSearch()}
                  />
                </Fade>
              )}
            </div>
            <Hidden mdDown>
              <div
                style={{
                  display: 'flex',
                  flexDirection: 'row',
                  marginRight: 24,
                }}
              >
                {this.renderGenreMenu('show')}
                {this.renderGenreMenu('movie')}
              </div>
            </Hidden>
            <Box display={{ xs: 'none', sm: 'none' }} m={1}>
              <ButtonLink
                color="inherit"
                primary="New, Arriving, &amp; Expiring"
                to="/new"
              />
            </Box>

            {!isAuthed && (
              <Button
                startIcon={
                  ['xs', 'sm'].includes(this.props.width) ? null : <Person />
                }
                className={classes.loginButton}
                onClick={this.openAuthDialog}
              >
                Login
              </Button>
            )}
            {this.isSmallDevice &&
              !mobileSearchBarOpen &&
              this.props.showToolbarSearch && (
                <div
                  className={classes.sectionMobile}
                  ref={this.mobileSearchIcon}
                >
                  <IconButton
                    aria-owns={'Search Teletracker'}
                    aria-haspopup="true"
                    onClick={this.handleMobileSearchDisplayOpen}
                    color="inherit"
                    disableRipple
                  >
                    <SearchIcon />
                  </IconButton>
                </div>
              )}
            {this.isSmallDevice &&
              mobileSearchBarOpen &&
              this.renderMobileSearchBar()}
          </MUIToolbar>
        </AppBar>
        <AuthDialog
          open={this.state.authDialogOpen}
          onClose={this.closeAuthDialog}
        />
      </React.Fragment>
    );
  }

  renderMobileSearchBar = () => {
    let { classes, drawerOpen } = this.props;

    return (
      <Slide
        direction="down"
        in={this.state.mobileSearchBarOpen}
        timeout={350}
        mountOnEnter
      >
        <div
          className={clsx(classes.sectionMobile, classes.mobileSearchContainer)}
        >
          <IconButton
            onClick={this.handleMobileSearchDisplayClose}
            color="inherit"
            size="small"
            className={classes.mobileSearchIcon}
          >
            <KeyboardArrowUp />
          </IconButton>
          <div className={classes.searchMobile}>
            <Search
              drawerOpen={drawerOpen}
              onDrawerChange={this.toggleDrawer}
              quickSearchEnabled={this.showQuickSearch()}
            />
          </div>
        </div>
      </Slide>
    );
  };
}

const mapStateToProps = (appState: AppState) => {
  return {
    isAuthed: !R.isNil(R.path(['auth', 'token'], appState)),
    genres: appState.metadata.genres,
    currentSearchText: R.path<string>(
      ['search', 'currentSearchText'],
      appState,
    ),
    currentQuickSearchText: R.path<string>(
      ['search', 'quick', 'currentSearchText'],
      appState,
    ),
  };
};

const mapDispatchToProps: (dispatch: Dispatch) => DispatchProps = dispatch => {
  return bindActionCreators(
    {
      logout,
    },
    dispatch,
  );
};

export default withWidth()(
  withRouter(
    withStyles(styles)(connect(mapStateToProps, mapDispatchToProps)(Toolbar)),
  ),
);
