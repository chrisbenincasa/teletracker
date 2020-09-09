import React, { MouseEvent, RefObject, useMemo, useRef, useState } from 'react';
import {
  AppBar,
  Box,
  Button,
  createStyles,
  Divider,
  Fade,
  Hidden,
  IconButton,
  makeStyles,
  MenuItem,
  MenuList,
  Paper,
  Popper,
  Slide,
  Theme,
  Toolbar as MUIToolbar,
  Typography,
} from '@material-ui/core';
import {
  AccountCircle,
  ArrowDropDown,
  ArrowDropUp,
  KeyboardArrowUp,
  Menu as MenuIcon,
  MenuOpen,
  Search as SearchIcon,
} from '@material-ui/icons';
import clsx from 'clsx';
import Link from 'next/link';
import { useRouter } from 'next/router';
import { hexToRGB } from '../../utils/style-utils';
import Search from './Search';
import AuthDialog from '../Auth/AuthDialog';
import useIsMobile, { useIsSmallScreen } from '../../hooks/useIsMobile';
import useStateSelector from '../../hooks/useStateSelector';
import { useWithUserContext } from '../../hooks/useWithUser';
import { useGenres } from '../../hooks/useStateMetadata';
import Telescope from '../../icons/Telescope';

const useStyles = makeStyles((theme: Theme) =>
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
    drawerButton: {
      marginRight: theme.spacing(1),
    },
  }),
);

interface MenuItemProps {
  readonly to: string;
  readonly primary?: string;
  readonly button?: boolean;
  readonly key?: string;
  readonly selected?: boolean;
  readonly listLength?: number;
  readonly onClick?: (event: React.MouseEvent<HTMLElement>) => void;
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

interface Props {
  readonly drawerOpen: boolean;
  readonly showToolbarSearch: boolean;
  readonly onDrawerChange: (close?: boolean) => void;
}

export default function Toolbar(props: Props) {
  const { drawerOpen, showToolbarSearch } = props;
  const classes = useStyles();
  const router = useRouter();
  const genres = useGenres();
  const isMobile = useIsMobile();
  const isSmallScreen = useIsSmallScreen();

  const isBooting = useStateSelector(state => state.startup.isBooting);
  const [mobileSearchBarOpen, setMobileSearchBarOpen] = useState(false);
  const [authDialogOpen, setAuthDialogOpen] = useState(false);
  const [genreType, setGenreType] = useState<string | undefined>();

  const genreMovieContainerRef = useRef<HTMLElement | null>(null);
  const genreShowContainerRef = useRef<HTMLElement | null>(null);
  const genreMovieSpacerRef = useRef<HTMLDivElement | null>(null);
  const genreShowSpacerRef = useRef<HTMLDivElement | null>(null);
  const mobileSearchIcon = useRef<HTMLDivElement | null>(null);
  const genreAnchorEl = useRef<HTMLButtonElement | null>(null);

  const currentSearchText = useStateSelector(
    state => state.search.currentSearchText,
  );
  const currentQuickSearchText = useStateSelector(
    state => state.search.quick.currentSearchText,
  );

  const { isLoggedIn, isCheckingAuth } = useWithUserContext();

  const showQuickSearch = useMemo(() => {
    // No need to show quickSearch on the search page if the text is the same.
    // It would just show results that are already present on the page.
    return !(
      currentQuickSearchText === currentSearchText &&
      router.pathname === '/search'
    );
  }, [currentQuickSearchText, currentSearchText, router]);

  const fireOnDrawerChange = (close?: boolean) => {
    props.onDrawerChange(close);
  };

  const openAuthDialog = () => setAuthDialogOpen(true);
  const closeAuthDialog = () => setAuthDialogOpen(false);

  const handleMobileSearchDisplayOpen = () => {
    fireOnDrawerChange(true);
    setMobileSearchBarOpen(true);
  };

  const handleMobileSearchDisplayClose = () => {
    setMobileSearchBarOpen(false);
  };

  const handleGenreMenuClose = () => {
    if (drawerOpen) {
      props.onDrawerChange();
    }

    genreAnchorEl.current = null;
    setGenreType(undefined);
  };

  const handleGenreMenu = (
    event: MouseEvent<HTMLButtonElement>,
    type: 'movie' | 'show' | undefined,
  ) => {
    // If quick search is open, close it & move focus to button
    //TODO this.resetSearchAnchor(event);
    // event.target?.focus();
    event.currentTarget.focus();

    // If user is on smaller device, go directly to page
    if (isSmallScreen) {
      router.push(`popular?type=${type}`);
      return;
    }

    // If Genre menu is already open and user is not navigating to submenu, close it
    // event.relatedTarget is target element in a mouseEnter/mouseExit event

    if (
      genreType === type &&
      event.relatedTarget !== genreMovieContainerRef?.current?.firstChild &&
      event.relatedTarget !== genreShowContainerRef?.current?.firstChild &&
      event.relatedTarget !== genreShowSpacerRef?.current &&
      event.relatedTarget !== genreMovieSpacerRef?.current
    ) {
      genreAnchorEl.current = null;
      setGenreType(undefined);
      return;
    }

    genreAnchorEl.current = event.currentTarget;
    setGenreType(type);
  };

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

  const renderMobileSearchBar = () => {
    return (
      <Slide
        direction="down"
        in={mobileSearchBarOpen}
        timeout={100}
        mountOnEnter
      >
        <div
          className={clsx(classes.sectionMobile, classes.mobileSearchContainer)}
        >
          <IconButton
            onClick={handleMobileSearchDisplayClose}
            color="inherit"
            size="small"
            className={classes.mobileSearchIcon}
          >
            <KeyboardArrowUp />
          </IconButton>
          <div className={classes.searchMobile}>
            <Search
              drawerOpen={drawerOpen}
              onDrawerChange={fireOnDrawerChange}
              quickSearchEnabled={showQuickSearch}
            />
          </div>
        </div>
      </Slide>
    );
  };

  const renderGenreMenu = (type: 'movie' | 'show') => {
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
            onClick={event => handleGenreMenu(event, type)}
            onMouseEnter={event => handleGenreMenu(event, type)}
            onMouseLeave={event => handleGenreMenu(event, type)}
            color="inherit"
            focusRipple={false}
            endIcon={
              isSmallScreen ? null : genreType === type ? (
                <ArrowDropUp />
              ) : (
                <ArrowDropDown />
              )
            }
          >
            {type === 'show'
              ? isSmallScreen
                ? 'Shows'
                : 'TV Shows'
              : 'Movies'}
          </Button>
          {genreType === type && (
            <div
              ref={type === 'show' ? genreShowSpacerRef : genreMovieSpacerRef}
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
          open={Boolean(genreType === type)}
          anchorEl={genreAnchorEl.current}
          placement="bottom-end"
          keepMounted
          transition
          style={{
            display: Boolean(genreType === type) ? 'block' : 'none',
          }}
        >
          {({ TransitionProps }) => (
            <Fade
              {...TransitionProps}
              in={Boolean(genreType === type)}
              timeout={200}
            >
              <Paper
                className={classes.genrePaper}
                onMouseLeave={handleGenreMenuClose}
                ref={
                  type === 'show'
                    ? genreShowContainerRef
                    : genreMovieContainerRef
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
                    onClick={handleGenreMenuClose}
                    to={`/${type}s`}
                    key={`explore-${type}`}
                    primary={`All ${type}s`}
                  />
                  <MenuItemLink
                    onClick={handleGenreMenuClose}
                    key={`popular-${type}`}
                    to={`/popular?type=${type}`}
                    primary={`Trending ${type}s`}
                  />
                  <MenuItemLink
                    onClick={handleGenreMenuClose}
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
                        onClick={handleGenreMenuClose}
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
  };

  return (
    <React.Fragment>
      <AppBar position="sticky">
        <MUIToolbar variant="dense" disableGutters>
          <IconButton
            focusRipple={false}
            onClick={() => fireOnDrawerChange()}
            color="inherit"
            className={classes.drawerButton}
          >
            {drawerOpen ? <MenuOpen /> : <MenuIcon />}
          </IconButton>
          <Telescope htmlColor="white" viewBox="0 0 865.5 865.5" />
          <Link href="/" passHref>
            <Typography
              variant="h6"
              color="inherit"
              component="a"
              style={{ textDecoration: 'none', padding: '0 4px' }}
            >
              Telescope
            </Typography>
          </Link>
          <div className={classes.grow}>
            {!isSmallScreen && showToolbarSearch && (
              <Fade in={true} timeout={500}>
                <Search
                  drawerOpen={drawerOpen}
                  onDrawerChange={fireOnDrawerChange}
                  quickSearchEnabled={showQuickSearch}
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
              {renderGenreMenu('show')}
              {renderGenreMenu('movie')}
            </div>
          </Hidden>
          <Box display={{ xs: 'none', sm: 'none' }} m={1}>
            <ButtonLink
              color="inherit"
              primary="New, Arriving, &amp; Expiring"
              to="/new"
            />
          </Box>

          {!isBooting &&
            !isLoggedIn &&
            (isMobile ? (
              <IconButton
                aria-owns={'Login'}
                onClick={openAuthDialog}
                color="inherit"
                disableRipple
              >
                <AccountCircle />
              </IconButton>
            ) : (
              <Button
                startIcon={<AccountCircle />}
                className={classes.loginButton}
                onClick={openAuthDialog}
              >
                Login
              </Button>
            ))}
          {isSmallScreen && !mobileSearchBarOpen && showToolbarSearch && (
            <div className={classes.sectionMobile} ref={mobileSearchIcon}>
              <IconButton
                aria-owns={'Search Telescope'}
                aria-haspopup="true"
                onClick={handleMobileSearchDisplayOpen}
                color="inherit"
                disableRipple
              >
                <SearchIcon />
              </IconButton>
            </div>
          )}
          {isSmallScreen && renderMobileSearchBar()}
        </MUIToolbar>
      </AppBar>
      <AuthDialog open={authDialogOpen} onClose={closeAuthDialog} />
    </React.Fragment>
  );
}
