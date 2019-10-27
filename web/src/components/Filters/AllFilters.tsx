import { Collapse, makeStyles, Theme, Typography } from '@material-ui/core';
import React from 'react';
import { Genre, ItemType, ListSortOptions, NetworkType } from '../../types';
import TypeToggle from './TypeToggle';
import NetworkSelect from './NetworkSelect';
import GenreSelect from './GenreSelect';
import SortDropdown from './SortDropdown';
import Sliders from './Sliders';

const useStyles = makeStyles((theme: Theme) => ({
  allFiltersContainer: {
    display: 'flex',
    flexWrap: 'wrap',
    padding: theme.spacing(2),
    backgroundColor: `${theme.palette.grey[800]}`,
  },
  filterSortContainer: {
    [theme.breakpoints.up('sm')]: {
      display: 'flex',
    },
    [theme.breakpoints.down('sm')]: {
      flexDirection: 'column',
    },
    zIndex: 1000,
    marginBottom: theme.spacing(1),
    flexGrow: 1,
  },
  filterTitle: { display: 'block', width: '100%' },
  genreContainer: {
    display: 'flex',
    flexDirection: 'row',
    width: '50%',
    flexWrap: 'wrap',
    [theme.breakpoints.down('sm')]: {
      width: '100%',
    },
  },
  slidersContainer: {
    display: 'flex',
    flexGrow: 1,
    margin: '10px 0',
    alignItems: 'flex-start',
    flexDirection: 'column',
  },
  networkContainer: {
    display: 'flex',
    margin: `${theme.spacing(1)}px 0`,
    alignItems: 'flex-start',
    flexGrow: 1,
  },
  toEdgeWrapper: {
    margin: `0 -${theme.spacing(2)}px ${theme.spacing(2)}px`,
  },
  typeContainer: {
    display: 'flex',
    margin: `${theme.spacing(1)}px 0`,
    alignItems: 'flex-start',
    flexGrow: 1,
  },
  sortContainer: {
    display: 'flex',
    flexDirection: 'column',
    margin: `${theme.spacing(1)}px`,
    alignItems: 'flex-start',
  },
}));

interface Props {
  handleGenreChange?: (genre?: number[]) => void;
  handleTypeChange?: (type?: ItemType[]) => void;
  handleNetworkChange?: (networkTypes?: NetworkType[]) => void;
  handleSortChange?: (sortOrder: ListSortOptions) => void;
  isListDynamic?: boolean;
  genres?: Genre[];
  open: boolean;
  disabledGenres?: number[];
}

const AllFilters = (props: Props) => {
  const classes = useStyles();
  const TIMEOUT_APPEAR = 600;
  const TIMEOUT_ENTER = 600;
  const TIMEOUT_EXIT = 300;
  const {
    disabledGenres,
    genres,
    isListDynamic,
    open,
    handleGenreChange,
    handleNetworkChange,
    handleTypeChange,
    handleSortChange,
  } = props;

  const setGenre = (genres?: number[]) => {
    handleGenreChange && handleGenreChange(genres);
  };

  const setType = (type?: ItemType[]) => {
    handleTypeChange && handleTypeChange(type);
  };

  const setNetworks = (networks?: NetworkType[]) => {
    handleNetworkChange && handleNetworkChange(networks);
  };

  const setSort = (sortOrder: ListSortOptions) => {
    handleSortChange && handleSortChange(sortOrder);
  };

  return (
    <Collapse
      in={open}
      timeout={{
        appear: TIMEOUT_APPEAR,
        enter: TIMEOUT_ENTER,
        exit: TIMEOUT_EXIT,
      }}
      className={classes.toEdgeWrapper}
      appear
    >
      <div className={classes.allFiltersContainer}>
        <Typography
          color="inherit"
          variant="h5"
          className={classes.filterTitle}
        >
          Filter
        </Typography>
        <div className={classes.filterSortContainer}>
          <div className={classes.genreContainer}>
            {handleGenreChange && (
              <GenreSelect
                genres={genres}
                disabledGenres={disabledGenres}
                handleChange={setGenre}
              />
            )}
          </div>
          <div className={classes.slidersContainer}>
            <Sliders />
          </div>
          <div className={classes.networkContainer}>
            {handleNetworkChange && (
              <NetworkSelect handleChange={setNetworks} />
            )}
          </div>
          <div className={classes.typeContainer}>
            {handleTypeChange && <TypeToggle handleChange={setType} />}
          </div>
          <div className={classes.sortContainer}>
            {handleSortChange && (
              <SortDropdown
                isListDynamic={!!isListDynamic}
                handleChange={setSort}
              />
            )}
          </div>
        </div>
      </div>
    </Collapse>
  );
};

export default AllFilters;
