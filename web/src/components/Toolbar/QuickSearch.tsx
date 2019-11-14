import {
  CircularProgress,
  Chip,
  ClickAwayListener,
  Fade,
  makeStyles,
  MenuList,
  MenuItem,
  Paper,
  Popper,
  Theme,
  Typography,
} from '@material-ui/core';
import { Rating } from '@material-ui/lab';
import _ from 'lodash';
import { ApiItem } from '../../types/v2';
import React from 'react';
import { truncateText } from '../../utils/textHelper';
import RouterLink from '../RouterLink';
import { getTmdbPosterImage } from '../../utils/image-helper';
import { formatRuntime } from '../../utils/textHelper';
import moment from 'moment';

const useStyles = makeStyles((theme: Theme) => ({
  noResults: {
    margin: theme.spacing(1),
    alignSelf: 'center',
  },
  progressSpinner: {
    margin: theme.spacing(1),
    justifySelf: 'center',
  },
  poster: {
    width: 50,
    boxShadow: '7px 10px 23px -8px rgba(0,0,0,0.57)',
    marginRight: theme.spacing(1),
  },
}));

interface Props {
  isSearching: boolean;
  searchAnchor?: HTMLInputElement | null;
  searchResults?: ApiItem[];
  searchText: string;
  handleResetSearchAnchor: (event) => void;
  handleSearchForSubmit: (event) => void;
}

function QuickSearch(props: Props) {
  const classes = useStyles();
  let { searchResults, isSearching, searchText, searchAnchor } = props;
  console.log(searchResults);

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
                    {searchResults && searchResults.length > 0 ? (
                      searchResults.slice(0, 3).map(result => {
                        const voteAverage =
                          result.ratings && result.ratings.length
                            ? result.ratings[0].vote_average
                            : 0;
                        const voteCount =
                          result.ratings && result.ratings.length
                            ? result.ratings[0].vote_count || 0
                            : 0;
                        const runtime =
                          (result.runtime &&
                            formatRuntime(result.runtime, result.type)) ||
                          null;

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
                            <div
                              style={{
                                display: 'flex',
                                flexDirection: 'column',
                                flexGrow: 1,
                              }}
                            >
                              <Typography variant="subtitle1">
                                {truncateText(result.original_title, 20)}
                                {` (${moment(result.release_date).format(
                                  'YYYY',
                                )})`}
                              </Typography>
                              <Rating
                                value={voteAverage / 2}
                                precision={0.1}
                                size="small"
                                readOnly
                              />
                              <Chip label={result.type} size="small" />
                            </div>
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
                    {searchResults && searchResults.length > 3 && (
                      <MenuItem
                        dense
                        style={{ justifyContent: 'center' }}
                        onClick={props.handleSearchForSubmit}
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

export default QuickSearch;
