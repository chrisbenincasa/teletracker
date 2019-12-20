import React, { useState, useRef } from 'react';
import {
  Fade,
  IconButton,
  InputBase,
  makeStyles,
  Theme,
} from '@material-ui/core';
import { Close, Search as SearchIcon } from '@material-ui/icons';
import { useHistory, useLocation } from 'react-router-dom';
import QuickSearch from './QuickSearch';
import { fade } from '@material-ui/core/styles/colorManipulator';
import _ from 'lodash';
import { useDispatch, useSelector } from 'react-redux';
import * as R from 'ramda';
import { AppState } from '../../reducers';
import { search } from '../../actions/search';

const useStyles = makeStyles((theme: Theme) => ({
  inputRoot: {
    color: 'inherit',
    width: '100%',
  },
  inputInput: {
    padding: theme.spacing(1, 1, 1, 10),
    transition: theme.transitions.create('width'),
    width: '100%',
    '&::-webkit-search-decoration,&::-webkit-search-cancel-button,&::-webkit-search-results-button,&::-webkit-search-results-decoration': {
      '-webkit-appearance': 'none',
    },
    caretColor: theme.palette.common.white,
  },
  search: {
    display: 'flex',
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
  },
  searchClear: {
    color: theme.palette.common.white,
    opacity: 0.25,
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
  const searchInput = useRef<HTMLInputElement>(null);

  const clearSearch = () => {
    let newSearchText = '';
    setSearchText(newSearchText);

    if (searchInput.current) {
      searchInput.current.value = '';
      searchInput.current.focus();
    }
  };

  const handleSearchChangeDebounced = _.debounce((target, newSearchText) => {
    if (searchAnchor === null && location.pathname !== '/search') {
      setSearchAnchor(target);
    }

    if (location.pathname === '/search') {
      setSearchAnchor(null);
    }

    if (newSearchText.length > 0) {
      execQuickSearch(newSearchText);
    }
  }, 250);

  const handleSearchChange = event => {
    let target = event.currentTarget;
    let newSearchText = target.value;
    setSearchText(newSearchText);
    handleSearchChangeDebounced(target, newSearchText);
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
    if (event.target !== searchInput.current) {
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

  return (
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
        inputRef={searchInput}
        onChange={handleSearchChange}
        onKeyDown={handleSearchForEnter}
        onFocus={handleSearchFocus}
      />
      {searchText.length > 0 ? (
        <Fade in={true}>
          <IconButton onClick={clearSearch} color="inherit" size="small">
            <Close className={classes.searchClear} />
          </IconButton>
        </Fade>
      ) : null}
      <QuickSearch
        searchText={searchText}
        isSearching={isSearching}
        searchResults={searchResults}
        searchAnchor={searchAnchor}
        handleResetSearchAnchor={resetSearchAnchor}
        handleSearchForSubmit={handleSearchForSubmit}
      />
    </div>
  );
}

export default Search;
