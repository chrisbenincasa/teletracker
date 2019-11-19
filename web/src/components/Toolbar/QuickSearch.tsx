import {
  CircularProgress,
  Chip,
  ClickAwayListener,
  Fade,
  Icon,
  makeStyles,
  MenuList,
  MenuItem,
  Paper,
  Popper,
  Theme,
  Typography,
} from '@material-ui/core';
import { Rating } from '@material-ui/lab';
import { Item } from '../../types/v2/Item';
import React from 'react';
import { truncateText } from '../../utils/textHelper';
import RouterLink from '../RouterLink';
import { getTmdbPosterImage } from '../../utils/image-helper';
import { formatRuntime } from '../../utils/textHelper';
import moment from 'moment';

const useStyles = makeStyles((theme: Theme) => ({
  chip: {
    margin: `${theme.spacing(1) / 2}px ${theme.spacing(1) /
      2}px ${theme.spacing(1) / 2}px 0`,
  },
  chipWrapper: {
    display: 'flex',
    flexDirection: 'row',
    alignItems: 'center',
  },
  itemDetails: {
    display: 'flex',
    flexDirection: 'column',
  },
  missingPoster: {
    display: 'flex',
    width: 50,
    marginRight: theme.spacing(1),
    height: 75,
    color: '#9e9e9e',
    backgroundColor: '#e0e0e0',
    fontSize: '3em',
  },
  missingPosterIcon: {
    alignSelf: 'center',
    margin: '0 auto',
    display: 'inline-block',
  },
  noResults: {
    padding: theme.spacing(1),
    alignSelf: 'center',
  },
  progressSpinner: {
    margin: theme.spacing(1),
    justifySelf: 'center',
  },
  poster: {
    width: 50,
    marginRight: theme.spacing(1),
  },
  searchWrapper: {
    height: 'auto',
    overflow: 'scroll',
    width: 338,
    backgroundColor: theme.palette.primary.main,
  },
  viewAllResults: { justifyContent: 'center', padding: theme.spacing(1) },
}));

interface Props {
  isSearching: boolean;
  searchAnchor?: HTMLInputElement | null;
  searchResults?: Item[];
  searchText: string;
  handleResetSearchAnchor: (event) => void;
  handleSearchForSubmit: (event) => void;
}

function QuickSearch(props: Props) {
  const classes = useStyles();
  let { searchResults, isSearching, searchText, searchAnchor } = props;

  return searchAnchor && searchText && searchText.length > 0 ? (
    <ClickAwayListener
      onClickAway={event => props.handleResetSearchAnchor(event)}
    >
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
              className={classes.searchWrapper}
              elevation={5}
            >
              <MenuList
                style={
                  isSearching
                    ? { display: 'flex', justifyContent: 'center', padding: 0 }
                    : { padding: 0 }
                }
              >
                {isSearching ? (
                  <CircularProgress className={classes.progressSpinner} />
                ) : (
                  <div>
                    {searchResults && searchResults.length > 0 ? (
                      searchResults.slice(0, 4).map(result => {
                        const voteAverage =
                          result.ratings && result.ratings.length
                            ? result.ratings[0].vote_average
                            : 0;
                        const runtime =
                          (result.runtime &&
                            formatRuntime(result.runtime, result.type)) ||
                          null;
                        const rating =
                          result.release_dates &&
                          result.release_dates.find(item => {
                            if (
                              item.country_code === 'US' &&
                              item.certification !== 'NR'
                            ) {
                              return item.certification;
                            } else {
                              return null;
                            }
                          });

                        return (
                          <MenuItem
                            dense
                            component={RouterLink}
                            to={result.relativeUrl}
                            key={result.id}
                            onClick={event =>
                              props.handleResetSearchAnchor(event)
                            }
                          >
                            {getTmdbPosterImage(result) ? (
                              <img
                                alt={`Movie poster for ${result.original_title}`}
                                src={`https://image.tmdb.org/t/p/w92${
                                  getTmdbPosterImage(result)!.id
                                }`}
                                className={classes.poster}
                              />
                            ) : (
                              <div className={classes.missingPoster}>
                                <Icon
                                  className={classes.missingPosterIcon}
                                  fontSize="inherit"
                                >
                                  broken_image
                                </Icon>
                              </div>
                            )}
                            <div className={classes.itemDetails}>
                              <Typography variant="subtitle1">
                                {truncateText(result.original_title, 32)}
                              </Typography>
                              <Rating
                                value={voteAverage / 2}
                                precision={0.1}
                                size="small"
                                readOnly
                              />
                              <div className={classes.chipWrapper}>
                                <Chip
                                  label={result.type}
                                  clickable
                                  size="small"
                                  className={classes.chip}
                                />
                                {rating && rating.certification && (
                                  <Chip
                                    label={rating.certification}
                                    clickable
                                    size="small"
                                    className={classes.chip}
                                  />
                                )}
                                {result.release_date && (
                                  <Chip
                                    label={moment(result.release_date).format(
                                      'YYYY',
                                    )}
                                    clickable
                                    size="small"
                                    className={classes.chip}
                                  />
                                )}
                                {runtime && (
                                  <Chip
                                    label={runtime}
                                    clickable
                                    size="small"
                                    className={classes.chip}
                                  />
                                )}
                              </div>
                            </div>
                          </MenuItem>
                        );
                      })
                    ) : (
                      <Typography
                        variant="body1"
                        align="center"
                        className={classes.noResults}
                      >
                        No results matching that search
                      </Typography>
                    )}
                    {searchResults && searchResults.length > 4 && (
                      <MenuItem
                        dense
                        className={classes.viewAllResults}
                        onClick={props.handleSearchForSubmit}
                      >
                        View All Results
                      </MenuItem>
                    )}
                  </div>
                )}
              </MenuList>
            </Paper>
          </Fade>
        )}
      </Popper>
    </ClickAwayListener>
  ) : null;
}

export default QuickSearch;
