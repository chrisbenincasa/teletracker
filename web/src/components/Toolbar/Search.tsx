import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  Fade,
  IconButton,
  InputBase,
  makeStyles,
  Theme,
} from '@material-ui/core';
import { Close, Search as SearchIcon } from '@material-ui/icons';
import QuickSearch from './QuickSearch';
import { fade } from '@material-ui/core/styles/colorManipulator';
import _ from 'lodash';
import { useDispatch, useSelector } from 'react-redux';
import * as R from 'ramda';
import { AppState } from '../../reducers';
import { quickSearch, search } from '../../actions/search';
import { FilterParams } from '../../utils/searchFilters';
import { calculateLimit } from '../../utils/list-utils';
import { useWidth } from '../../hooks/useWidth';
import { useRouter } from 'next/router';

const useStyles = makeStyles((theme: Theme) => ({
  inputRoot: {
    color: 'inherit',
    width: '100%',
  },
  inputInput: {
    padding: theme.spacing(1, 1, 1, 9),
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
  searchClearButton: {
    padding: theme.spacing(0.5),
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
  readonly drawerOpen?: boolean;
  readonly onDrawerChange?: (close?: boolean) => void;
  readonly inputStyle?: object;
  readonly filters?: FilterParams;
  readonly quickSearchColor?: string;
  readonly quickSearchEnabled?: boolean;
  readonly resetFocus?: boolean;
}

function Search(props: Props) {
  const classes = useStyles();
  // const location = useLocation();
  // const history = useHistory();
  const router = useRouter();
  const dispatch = useDispatch();
  const width = useWidth();
  const currentSearchText = useSelector((state: AppState) =>
    R.path<string>(['search', 'currentSearchText'], state),
  );
  const currentQuickSearchText = useSelector((state: AppState) =>
    R.path<string>(['search', 'quick', 'currentSearchText'], state),
  );
  const isQuickSearching = useSelector(
    (state: AppState) => state.search.quick.searching,
  );
  const quickSearchResults = useSelector(
    (state: AppState) => state.search.quick.results,
  );
  const [searchText, setSearchText] = useState<string>('');
  const [searchAnchor, setSearchAnchor] = useState<HTMLInputElement | null>(
    null,
  );
  const searchInput = useRef<HTMLInputElement>(null);

  useEffect(() => {
    searchInput.current && searchInput.current.focus();
  }, []);

  useEffect(() => {
    // Todo: investigate weird flash that happens without this timeout.
    setTimeout(function() {
      searchInput.current && searchInput.current.focus();
    }, 250);

    if (searchAnchor === null) {
      setSearchAnchor(searchInput.current);
    }
  }, [props.resetFocus]);

  // When search text changes, execute throttled/debounced search
  useEffect(() => {
    if (searchText.length > 0) {
      if (searchText.length < 5 || searchText.endsWith(' ')) {
        handleSearchChangeThrottled(searchText);
      } else {
        handleSearchChangeDebounced(searchText);
      }
    }
  }, [searchText]);

  const clearSearch = () => {
    let newSearchText = '';
    setSearchText(newSearchText);

    if (searchInput.current) {
      searchInput.current.value = '';
      searchInput.current.focus();
    }
  };

  const execQuickSearch = (text: string) => {
    // No need to search again
    if (currentQuickSearchText !== text) {
      dispatch(
        quickSearch({
          query: text,
          limit: 5,
        }),
      );
    }
  };

  const handleSearchChangeDebounced = useCallback(
    _.debounce(execQuickSearch, 500),
    [],
  );
  const handleSearchChangeThrottled = useCallback(
    _.throttle(execQuickSearch, 500),
    [],
  );

  // https://www.peterbe.com/plog/how-to-throttle-and-debounce-an-autocomplete-input-in-react
  const handleSearchChange = event => {
    let target = event.currentTarget;
    let newSearchText = target.value;
    setSearchText(newSearchText);

    if (searchAnchor === null) {
      setSearchAnchor(target);
    }
  };

  const handleSearchFocus = event => {
    if (props.drawerOpen && !!props.onDrawerChange) {
      props.onDrawerChange();
    }

    if (searchAnchor === null) {
      setSearchAnchor(event.currentTarget);
    }
  };

  const onSearchPage = () => {
    return location.pathname.toLowerCase() === '/search';
  };

  const handleSearchForSubmit = event => {
    if (onSearchPage()) {
      window.scrollTo(0, 0);
      setSearchAnchor(null);
    } else {
      setSearchAnchor(event);
    }
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
    if (router.pathname !== '/search') {
      router.push(`/search?q=${encodeURIComponent(text)}`);
    } else {
      router.push(`${router.pathname}?q=${encodeURIComponent(text)}`);
    }

    if (text.length >= 1 && currentSearchText !== text) {
      dispatch(
        search({
          query: text,
          limit: calculateLimit(width, 3, 0),
        }),
      );
    }
  };

  return (
    <div className={classes.search}>
      <div className={classes.searchIcon}>
        <IconButton
          onClick={() => execSearch(searchText)}
          color="inherit"
          size="small"
        >
          <SearchIcon />
        </IconButton>
      </div>
      <InputBase
        defaultValue={onSearchPage() ? currentSearchText : null}
        key={`${currentSearchText}-${onSearchPage()}`}
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
        style={props.inputStyle || undefined}
      />
      {searchText.length > 0 ||
      (currentSearchText && currentSearchText.length > 0) ? (
        <Fade in={true}>
          <IconButton
            onClick={clearSearch}
            color="inherit"
            size={location.pathname === '/search' ? 'medium' : 'small'}
            className={classes.searchClearButton}
          >
            <Close className={classes.searchClear} />
          </IconButton>
        </Fade>
      ) : null}
      {props.quickSearchEnabled && (
        <QuickSearch
          searchText={searchText}
          isSearching={isQuickSearching}
          searchResults={quickSearchResults}
          searchAnchor={searchAnchor}
          handleResetSearchAnchor={resetSearchAnchor}
          handleSearchForSubmit={handleSearchForSubmit}
          color={props.quickSearchColor || undefined}
        />
      )}
    </div>
  );
}

Search.defaultProps = {
  quickSearchEnabled: true,
  resetFocus: false,
};

export default Search;
