import React, { useEffect, useState, useRef } from 'react';
import {
  CircularProgress,
  Chip,
  ClickAwayListener,
  Fade,
  Icon,
  IconButton,
  InputBase,
  makeStyles,
  MenuList,
  MenuItem,
  Paper,
  Popper,
  Theme,
  Typography,
} from '@material-ui/core';
import { Rating } from '@material-ui/lab';
import { Menu as MenuIcon, Search as SearchIcon } from '@material-ui/icons';
import { useHistory, useLocation } from 'react-router-dom';
import { Item } from '../../types/v2/Item';
import { truncateText } from '../../utils/textHelper';
import RouterLink from '../RouterLink';
import { getTmdbPosterImage } from '../../utils/image-helper';
import { formatRuntime } from '../../utils/textHelper';
import moment from 'moment';
import { Genre as GenreModel } from '../../types';
import QuickSearch from './QuickSearch';
import { fade } from '@material-ui/core/styles/colorManipulator';
import _ from 'lodash';
import { useDispatch, useSelector } from 'react-redux';
import * as R from 'ramda';
import { AppState } from '../../reducers';
import { search, SearchInitiatedPayload } from '../../actions/search';

const useStyles = makeStyles((theme: Theme) => ({
  inputRoot: {
    color: 'inherit',
    width: '100%',
  },
  inputInput: {
    padding: theme.spacing(1, 1, 1, 10),
    transition: theme.transitions.create('width'),
    width: '100%',
    [theme.breakpoints.up('md')]: {
      width: 250,
    },
    '&::-webkit-search-decoration,&::-webkit-search-cancel-button,&::-webkit-search-results-button,&::-webkit-search-results-decoration': {
      '-webkit-appearance': 'none',
    },
    caretColor: theme.palette.common.white,
  },
  search: {
    position: 'relative',
    borderRadius: theme.shape.borderRadius,
    backgroundColor: fade(theme.palette.common.white, 0.15),
    '&:hover': {
      backgroundColor: fade(theme.palette.common.white, 0.25),
    },
    [theme.breakpoints.up('sm')]: {
      maxWidth: 720,
      margin: '0 auto',
    },
    width: '100%',
    [theme.breakpoints.down('sm')]: {
      display: 'none',
    },
  },
  sectionDesktop: {
    display: 'none',
    [theme.breakpoints.up('md')]: {
      display: 'block',
    },
  },
  sectionMobile: {
    display: 'flex',
    justifyContent: 'flex-end',
    [theme.breakpoints.up('md')]: {
      display: 'none',
    },
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
}));

interface Props {
  drawerOpen: boolean;
  onDrawerChange: (close?: boolean) => void;
}

function Search(props: Props) {
  const classes = useStyles();
  const location = useLocation();
  const history = useHistory();
  const dispatch = useDispatch();
  const currentSearchText = useSelector((state: AppState) =>
    R.path<string>(['search', 'currentSearchText'], state),
  );
  const isSearching = useSelector((state: AppState) => state.search.searching);
  const searchResults = useSelector((state: AppState) => state.search.results);
  const [searchText, setSearchText] = useState<string>('');
  const [searchAnchor, setSearchAnchor] = useState<HTMLInputElement | null>(
    null,
  );
  const [mobileSearchBarOpen, setMobileSearchBarOpen] = useState<boolean>(
    false,
  );
  const mobileSearchIcon = useRef<HTMLDivElement>(null);
  const mobileSearchInput = useRef<HTMLInputElement>(null);
  const desktopSearchInput = useRef<HTMLInputElement>(null);

  const clearSearch = () => {
    let searchText = '';
    setSearchText(searchText);
    mobileSearchInput.current && mobileSearchInput.current.focus();
  };

  const handleSearchChangeDebounced = _.debounce((target, searchText) => {
    if (searchAnchor === null && location.pathname !== '/search') {
      setSearchAnchor(target);
    }

    if (location.pathname === '/search') {
      setSearchAnchor(null);
    }

    if (searchText.length > 0) {
      execQuickSearch(searchText);
    }
  }, 250);

  const handleSearchChange = event => {
    let target = event.currentTarget;
    let searchText = target.value;
    setSearchText(searchText);
    handleSearchChangeDebounced(target, searchText);
  };

  const handleSearchFocus = event => {
    if (props.drawerOpen) {
      props.onDrawerChange();
    }

    if (searchAnchor === null && location.pathname !== '/search') {
      setSearchAnchor(event.currentTarget);
    }
  };

  const handleSearchForSubmit = event => {
    setSearchAnchor(event);
    execSearch(searchText);
  };

  const handleSearchForEnter = (
    event: React.KeyboardEvent<HTMLInputElement>,
  ) => {
    if (event.keyCode === 13) {
      execSearch(searchText);
      event.currentTarget.blur();
      setSearchAnchor(null);
    }
  };

  const resetSearchAnchor = event => {
    // If user is clicking back into search field, don't resetAnchor
    if (event.target !== desktopSearchInput.current) {
      setSearchAnchor(null);
    }
  };

  const execSearch = (text: string) => {
    if (location.pathname !== '/search') {
      history.push(`/search?q=${encodeURIComponent(text)}`);
    } else {
      history.push(`?q=${encodeURIComponent(text)}`);
    }

    if (text.length >= 1 && currentSearchText !== text) {
      dispatch(
        search({
          query: text,
        }),
      );
    }
  };

  const execQuickSearch = (text: string) => {
    if (text.length >= 1 && currentSearchText !== text) {
      if (location.pathname === '/search') {
        history.push(`?q=${encodeURIComponent(text)}`);
      }

      dispatch(
        search({
          query: text,
        }),
      );
    }
  };

  const debouncedExecSearch = _.debounce(execQuickSearch, 250);

  const handleMobileSearchDisplayOpen = () => {
    setMobileSearchBarOpen(true);
    mobileSearchInput.current && mobileSearchInput.current.focus();
  };

  const handleMobileSearchDisplayClose = () => {
    setMobileSearchBarOpen(false);
    mobileSearchIcon.current && mobileSearchIcon.current.focus();
  };

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
            inputRef={desktopSearchInput}
            onChange={handleSearchChange}
            onKeyDown={handleSearchForEnter}
            onFocus={handleSearchFocus}
          />
          <QuickSearch
            searchText={searchText}
            isSearching={isSearching}
            searchResults={searchResults}
            searchAnchor={searchAnchor}
            handleResetSearchAnchor={resetSearchAnchor}
            handleSearchForSubmit={handleSearchForSubmit}
          />
        </div>
      </div>

      <div className={classes.sectionMobile} ref={mobileSearchIcon}>
        <IconButton
          aria-owns={'Search Teletracker'}
          aria-haspopup="true"
          onClick={handleMobileSearchDisplayOpen}
          color="inherit"
          disableRipple
        >
          <SearchIcon />
        </IconButton>
      </div>
    </React.Fragment>
  );
}

export default Search;
